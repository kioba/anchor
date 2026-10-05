package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorSink
import dev.kioba.anchor.Created
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.Event
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.SignalScope
import dev.kioba.anchor.SubscriptionScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext

/**
 * Events the bus holds for its slowest `connect()` handler before `emit {}`
 * suspends. Without this buffer, an action a handler runs (or an error handler
 * invoked from it) that calls `emit {}` waits on that same handler, wedging
 * the bus for good.
 */
private const val EVENT_BUFFER_CAPACITY: Int = 64

/**
 * The dispatcher `connect()` handlers run on. While [starting], it runs every
 * coroutine in place instead of dispatching it, including the ones operators
 * such as `flatMapLatest`, `buffer` or `combine` start to collect upstream, so
 * a handler launched then runs until everything it started is suspended.
 * Afterwards it dispatches to [delegate] like any other dispatcher. A handler
 * coroutine resumed from another thread during that short window runs in
 * place on that thread.
 */
private class HandlerDispatcher(
  private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
  @Volatile
  var starting: Boolean = true

  override fun isDispatchNeeded(context: CoroutineContext): Boolean =
    !starting && delegate.isDispatchNeeded(context)

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable,
  ): Unit =
    delegate.dispatch(context, block)
}

@PublishedApi
internal class AnchorRuntime<R, S, Err>(
  val initialState: () -> S,
  val effectScope: () -> R,
  internal val init: (suspend Anchor<R, S, Err>.() -> Unit)? = null,
  internal val subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)? = null,
  internal val onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)? = null,
  internal val defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)? = null,
) : AnchorSink<R, S, Err>()
  where
        R : Effect,
        S : ViewState,
        Err : Any {
  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _viewState: MutableStateFlow<S> = MutableStateFlow(initialState())

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _signals: MutableSharedFlow<SignalProvider> =
    MutableSharedFlow(extraBufferCapacity = 64)

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _emitter: MutableSharedFlow<Event> =
    MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER_CAPACITY)

  /**
   * Guards [startupEvents] and [live]. Never held across `_emitter.emit`,
   * so a handler action that emits while the flush waits on that handler
   * cannot deadlock.
   */
  private val startupLock = Mutex()

  /**
   * Events emitted before the bus goes live, in call order: those `init`
   * emits, and any emitted by actions that run before the handlers start.
   * [goLive] flushes them to the bus once every handler has started.
   */
  private val startupEvents: MutableList<Event> = mutableListOf()

  /** Whether `emit {}` sends straight to the bus instead of queuing. */
  private var live: Boolean = false

  /**
   * Map storing cancellable jobs keyed by their identifier.
   * Access is guarded by [jobsMutex] to prevent race conditions.
   */
  @PublishedApi
  internal val jobs: MutableMap<Any, Job> = mutableMapOf()

  /**
   * Mutex protecting access to the [jobs] map to ensure thread-safe
   * job cancellation and prevent race conditions.
   */
  private val jobsMutex = Mutex()

  internal val effect = effectScope()

  override val viewState: StateFlow<S> = _viewState.asStateFlow()

  override val signals: SharedFlow<SignalProvider> = _signals.asSharedFlow()

  private val emitter: SharedFlow<Event> =
    _emitter
      .asSharedFlow()
      .onSubscription { emit(Created) }

  internal suspend fun consumeInitial() {
    init?.invoke(this@AnchorRuntime)
  }

  private suspend fun <T : Event> SharedFlow<T>.handlers(): List<Flow<*>> =
    SubscriptionsScope<R, S, Err>(
      chain = this,
      anchor = this@AnchorRuntime,
      effect = effect,
      onDomainError = onDomainError,
      defect = defect,
    ).also { scope -> subscriptions?.invoke(scope) }
      .flows
      .map { flow ->
        flow.catch { e ->
          safeExecute(this@AnchorRuntime, onDomainError, defect) {
            throw e
          }
        }
      }

  /**
   * Starts every `connect()` handler in place, each until it suspends, then
   * flushes the events queued until now to the bus and sends later
   * `emit {}` calls straight to it. Never waits for a handler to subscribe.
   *
   * A handler that collects its event flow in its own coroutines has
   * subscribed before the flush, so it receives [Created], then every
   * queued event in order, then live ones. One whose subscription waits on
   * anything else (`flowOn` another dispatcher, a scope from outside,
   * asynchronous work before it collects) subscribes later and misses what
   * was emitted before then. One that never collects its event flow never
   * subscribes. Neither holds up the caller. The flush runs even when
   * subscription setup throws, so events never stay queued.
   */
  suspend fun CoroutineScope.subscribe(): Job {
    try {
      val handlers = emitter.handlers()
      // SupervisorJob: a thrown subscription must not cancel siblings.
      val supervisor = SupervisorJob(parent = this.coroutineContext[Job])
      // Without a handler, an exception a subscription rethrows past
      // safeExecute (no defect handler configured) reaches each platform's
      // default uncaught-exception path. The JVM/Android default just logs it;
      // Kotlin/Native's default aborts the whole process. This handler makes
      // "contained, not fatal" consistent across platforms — the isolation
      // SupervisorJob provides already keeps it from touching sibling flows.
      val containment = CoroutineExceptionHandler { _, _ -> }
      val dispatcher =
        HandlerDispatcher(
          delegate = this.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default,
        )
      val supervised = CoroutineScope(this.coroutineContext + supervisor + containment + dispatcher)
      try {
        for (flow in handlers) {
          flow.launchIn(supervised)
        }
      } finally {
        dispatcher.starting = false
      }
      return supervisor
    } finally {
      goLive()
    }
  }

  /**
   * Flushes the startup queue to the bus in order, then sends every later
   * `emit {}` straight to the bus. An event emitted during the flush is
   * queued behind the rest and goes out in a later batch. The bus goes live
   * only once a batch comes back empty.
   */
  private suspend fun goLive() {
    do {
      val batch =
        startupLock.withLock {
          startupEvents.toList().also {
            startupEvents.clear()
            if (it.isEmpty()) live = true
          }
        }
      for (event in batch) _emitter.emit(event)
    } while (batch.isNotEmpty())
  }

  // DSL

  override val state: S
    get() = _viewState.value

  override fun reduce(
    reducer: S.() -> S,
  ): Unit =
    _viewState.update(reducer)

  override fun raise(
    error: Err,
  ): Nothing =
    throw RaisedException(error)

  override fun orDie(
    error: Err,
  ): Nothing =
    throw DomainDefectException(error)

  override suspend fun <T> effect(
    coroutineContext: CoroutineContext,
    block: suspend R.() -> T,
  ): T =
    withContext(coroutineContext) {
      effect.block()
    }

  /**
   * Executes a cancellable operation identified by [key].
   *
   * If a previous operation with the same key is still running, it will be cancelled
   * before the new operation starts. This is useful for debouncing operations like
   * search queries where only the latest request should run.
   *
   * Thread-safe: Uses a mutex to prevent race conditions when multiple cancellable
   * operations with the same key are triggered concurrently.
   *
   * Memory-safe: Completed jobs are automatically cleaned up from the jobs map in all
   * scenarios - successful completion, exceptions, or cancellation. The cleanup uses
   * identity comparison to ensure a job only removes itself, never a newer job that
   * may have replaced it.
   *
   * @param key Identifier for this cancellable operation. Operations with the same
   *        key will cancel each other.
   * @param block The operation to execute. If a previous operation with the same key
   *        is running, it will be cancelled before this block executes.
   */
  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    coroutineScope {
      var raised: RaisedException? = null

      val jobToWait =
        jobsMutex.withLock {
          // Cancel and remove old job if it exists
          val oldJob = jobs.remove(key)
          oldJob?.cancelAndJoin()

          // Create new job (don't wait while holding lock!)
          val newJob =
            launch {
              try {
                block()
              } catch (e: RaisedException) {
                raised = e
                throw e
              } finally {
                // Clean up completed job to prevent memory leak
                // Only remove if this job is still the current one for this key
                jobsMutex.withLock {
                  if (jobs[key] === coroutineContext[Job]) {
                    jobs.remove(key)
                  }
                }
              }
            }

          // Store the new job while still holding the lock
          newJob.also { jobs[key] = it }
        }

      // Wait for the job to complete (outside the lock)
      jobToWait.join()

      // Propagate RaisedException after join — CancellationException semantics
      // cause join() to complete normally, but the domain error must propagate.
      raised?.let { throw it }
    }
  }

  override suspend fun post(
    block: SignalScope.() -> Signal,
  ) {
    val signal = SignalScope.block()
    _signals.emit(SignalProvider { signal })
  }

  override suspend fun emit(
    block: SubscriptionScope.() -> Event,
  ) {
    val event = SubscriptionScope.block()
    val queued =
      startupLock.withLock {
        if (!live) startupEvents += event
        !live
      }
    if (!queued) _emitter.emit(event)
  }
}
