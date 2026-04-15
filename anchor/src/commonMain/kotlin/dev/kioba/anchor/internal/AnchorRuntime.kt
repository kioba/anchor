package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorSink
import dev.kioba.anchor.Created
import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.SignalScope
import dev.kioba.anchor.SubscriptionScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

@PublishedApi
internal class AnchorRuntime<R, S, Err>(
  val initialState: () -> S,
  val effectScope: () -> R,
  internal val init: (suspend Anchor<R, S, Err>.() -> Unit)? = null,
  internal val subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)? = null,
  internal val onDomainError: (suspend Anchor<R, S, Err>.(Err) -> Unit)? = null,
  internal val defect: (suspend Anchor<R, S, Err>.(Throwable) -> Unit)? = null,
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
  internal val _emitter: MutableSharedFlow<Event> = MutableSharedFlow()

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

  private suspend fun <T : Event> SharedFlow<T>.handlers(): Flow<Any?> =
    SubscriptionsScope<R, S, Err>(this, anchor = this@AnchorRuntime, effect = effect)
      .also { scope -> subscriptions?.invoke(scope) }
      .flows
      .merge()

  suspend fun CoroutineScope.subscribe(): Job =
    emitter
      .handlers()
      .launchIn(this)

  // DSL

  override val state: S
    get() = _viewState.value

  override fun reduce(
    reducer: S.() -> S,
  ): Unit =
    _viewState.update(reducer)

  override fun raise(error: Err): Nothing =
    throw RaisedException(error)

  override fun orDie(error: Err): Nothing =
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
  ): Unit =
    _emitter
      .emit(SubscriptionScope.block())
}
