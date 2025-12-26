package dev.kioba.anchor

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

@PublishedApi
internal class AnchorRuntime<E, S>(
  public val initialState: () -> S,
  public val effectScope: () -> E,
  internal val init: (suspend Anchor<E, S>.() -> Unit)? = null,
  internal val subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)? = null,
) : AnchorSink<E, S>()
  where
E : Effect,
S : ViewState {

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _viewState: MutableStateFlow<S> = MutableStateFlow(initialState())

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _signals: MutableSharedFlow<SignalProvider> = MutableSharedFlow()

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

  private val emitter: SharedFlow<Event> = _emitter.asSharedFlow()
    .onSubscription { emit(Created) }

  internal suspend fun consumeInitial() {
    init?.invoke(this@AnchorRuntime)
  }

  private suspend fun <T : Event> SharedFlow<T>.handlers(): Flow<Any?> =
    SubscriptionsScope(this, anchor = this@AnchorRuntime, effect = effect)
      .also { scope -> subscriptions?.invoke(scope) }
      .flows
      .merge()

  public suspend fun CoroutineScope.subscribe(): Job =
    emitter
      .handlers()
      .launchIn(this)

  // DSL

  public override val state: S
    get() = _viewState.value

  public override fun reduce(
    reducer: S.() -> S,
  ): Unit = _viewState.update(reducer)

  override suspend fun <R> effect(
    coroutineContext: CoroutineContext,
    block: suspend E.() -> R,
  ): R =
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
   * Memory-safe: Completed jobs are automatically cleaned up from the jobs map.
   *
   * @param key Identifier for this cancellable operation. Operations with the same
   *        key will cancel each other.
   * @param block The operation to execute. If a previous operation with the same key
   *        is running, it will be cancelled before this block executes.
   */
  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<E, S>.() -> Unit,
  ) {
    jobsMutex.withLock {
      // Cancel and remove old job if it exists
      val oldJob = jobs.remove(key)
      oldJob?.cancelAndJoin()

      // Create and store new job atomically
      val newJob = coroutineScope {
        launch {
          try {
            block()
          } finally {
            // Clean up completed job to prevent memory leak
            jobsMutex.withLock {
              jobs.remove(key)
            }
          }
        }
      }

      // Store the new job while still holding the lock
      jobs[key] = newJob
    }
  }

  override suspend fun post(
    block: SignalScope.() -> Signal
  ) {
    val signal = SignalScope.block()
    _signals.emit(SignalProvider { signal })
  }

  override suspend fun emit(
    block: SubscriptionScope.() -> Event
  ): Unit =
    _emitter
      .emit(SubscriptionScope.block())
}
