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

  @PublishedApi
  internal val jobs: MutableMap<Any, Job> = mutableMapOf()

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

  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<E, S>.() -> Unit,
  ) {
    val oldJob = jobs[key]
    jobs[key] =
      coroutineScope {
        launch {
          oldJob?.cancelAndJoin()
          block()
        }
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
