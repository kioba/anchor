package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorSink
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
   * closed, and emptied, when [subscribe] ends: the `subscriptions` block
   * threw, or subscriptions were torn down. `emit {}` then drops events,
   * since no handler can receive them.
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

  /**
   * Runs the `subscriptions` block and returns its `connect()` handlers, each
   * with its own inbox, and each flow wrapped so a failure it does not handle
   * is routed to `onDomainError` / `defect`.
   */
  private suspend fun handlers(): List<InboxedFlow> =
    SubscriptionsScope<R, S, Err>(
      anchor = this@AnchorRuntime,
      effect = effect,
      onDomainError = onDomainError,
      defect = defect,
    ).also { scope ->
      subscriptions?.invoke(scope)
    }.handlers
      .map { handler ->
        InboxedFlow(
          inbox = handler.inbox,
          flow =
            handler.flow
              .catch { e ->
                safeExecute(this@AnchorRuntime, onDomainError, defect) {
                  throw e
                }
              }.onCompletion { handler.inbox.close() },
        )
      }

  /**
   * Runs the `subscriptions` block, which gives every `connect()` handler an
   * inbox, then collects every handler and, after them, the forwarder, until
   * the calling coroutine is cancelled. The forwarder moves every event from
   * the [events] channel into every inbox, in order: first the ones emitted
   * before now, then later ones. It goes last, so every handler that collects
   * its events directly is already reading when delivery starts.
   *
   * Each handler receives [dev.kioba.anchor.Created], then every event delivered to its inbox,
   * however late it starts collecting, as long as its inbox did not fill up
   * before then. A handler that ends, by completing or failing, ends alone:
   * its inbox is closed and emptied, and delivery to the others carries on.
   * When the block throws, or this is cancelled, the [events] channel is
   * closed and emptied, and later `emit {}` calls drop their events.
   */
  suspend fun subscribe() {
    try {
      val handlers = handlers()
      val forwarder =
        events
          .receiveAsFlow()
          .onEach { event -> handlers.forEach { it.inbox.deliver(event) } }
      (handlers.map { it.flow } + forwarder).collectSupervised()
    } finally {
      closeEvents()
    }
  }

  /**
   * Closes and empties the [events] channel once no handler can ever receive
   * an event: subscriptions ended, or never started because the anchor was
   * torn down first. Later `emit {}` calls drop their events. Safe to call
   * more than once.
   */
  internal fun closeEvents() {
    events.close()
    events.drain()
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
    }
  }
}
