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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext

/**
 * Capacity of the channel `emit {}` sends into. The channel holds every event emitted before the handlers start, so `init`
 * can emit at most this many before it waits on handlers that start only once
 * it returns.
 */
private const val EVENT_BUFFER_CAPACITY: Int = 64

/**
 * Capacity of each handler's inbox. A handler whose events are collected by
 * a coroutine an operator starts (`flatMapLatest`, `buffer`, `combine`) starts
 * reading a moment after delivery starts, and until it does, an event that
 * finds its inbox full is dropped. So an inbox holds the whole startup backlog
 * (at most [EVENT_BUFFER_CAPACITY] events) plus [Created], with as much again
 * to spare for events emitted before the handler gets going.
 */
private const val INBOX_CAPACITY: Int = 2 * EVENT_BUFFER_CAPACITY

/**
 * One `connect()` handler's own queue of events, created when the handler is
 * declared and starting with [Created]. It exists before the handler runs, so
 * the handler receives every event delivered to it, however late it starts
 * collecting.
 *
 * While the handler is collecting [input], delivery waits for room, so a slow
 * handler holds up `emit {}` instead of losing events. While it is not, an
 * event that finds the inbox full is dropped, so a handler that never reads
 * its input, or has stopped, holds nothing up.
 */
private class Inbox {
  private val channel = Channel<Event>(INBOX_CAPACITY).apply { trySend(Created) }

  @Volatile
  private var reading: Boolean = false

  val input: Flow<Event> =
    flow {
      reading = true
      try {
        emitAll(channel.receiveAsFlow())
      } finally {
        reading = false
      }
    }

  suspend fun deliver(
    event: Event,
  ) {
    if (reading) channel.send(event) else channel.trySend(event)
  }
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

  /**
   * Every `emit {}` goes through this channel, in call order. Nothing reads
   * it until [subscribe] has created the handlers' inboxes and launched the
   * forwarder, so the events `init` emits, and any emitted by actions before
   * then, wait here. Its one reader keeps the order without a lock. It is
   * closed only when the `subscriptions` block throws; `emit {}` then drops
   * events, since there is no handler to receive them.
   */
  private val events: Channel<Event> = Channel(EVENT_BUFFER_CAPACITY)

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

  internal suspend fun consumeInitial() {
    init?.invoke(this@AnchorRuntime)
  }

  private suspend fun handlers(
    inboxes: MutableList<Inbox>,
  ): List<Flow<*>> =
    SubscriptionsScope<R, S, Err>(
      chain = { Inbox().also(inboxes::add).input },
      anchor = this@AnchorRuntime,
      effect = effect,
      onDomainError = onDomainError,
      defect = defect,
    ).also { scope ->
      subscriptions?.invoke(scope)
    }.flows
      .map { flow ->
        flow.catch { e ->
          safeExecute(this@AnchorRuntime, onDomainError, defect) {
            throw e
          }
        }
      }

  /**
   * Runs the `subscriptions` block, which gives every `connect()` handler an
   * inbox, launches the handlers, and launches the forwarder beside them under
   * the returned supervisor. The forwarder moves every event from the [events]
   * channel into every inbox, in order, until that supervisor is cancelled:
   * first the ones emitted before now, then later ones.
   *
   * Each handler receives [Created], then every event delivered to its inbox,
   * however late it starts collecting, as long as its inbox did not fill up
   * before then. When the block throws, the channel is closed and later
   * `emit {}` calls drop their events.
   */
  suspend fun CoroutineScope.subscribe(): Job {
    val inboxes = mutableListOf<Inbox>()
    val handlers =
      try {
        handlers(inboxes)
      } catch (e: Throwable) {
        events.close()
        throw e
      }
    // SupervisorJob: a thrown subscription must not cancel siblings.
    val supervisor = SupervisorJob(parent = this.coroutineContext[Job])
    // Without a handler, an exception a subscription rethrows past
    // safeExecute (no defect handler configured) reaches each platform's
    // default uncaught-exception path. The JVM/Android default just logs it;
    // Kotlin/Native's default aborts the whole process. This handler makes
    // "contained, not fatal" consistent across platforms — the isolation
    // SupervisorJob provides already keeps it from touching sibling flows.
    val containment = CoroutineExceptionHandler { _, _ -> }
    val supervised = CoroutineScope(this.coroutineContext + supervisor + containment)
    // Undispatched: each handler runs until it first suspends, so one that
    // collects its events directly is already reading before delivery starts.
    for (flow in handlers) {
      supervised.launch(start = CoroutineStart.UNDISPATCHED) { flow.collect() }
    }
    supervised.launch {
      for (event in events) {
        for (inbox in inboxes) inbox.deliver(event)
      }
    }
    return supervisor
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
    try {
      events.send(event)
    } catch (_: ClosedSendChannelException) {
      // The subscriptions block failed: no handler exists to receive it.
    }
  }
}
