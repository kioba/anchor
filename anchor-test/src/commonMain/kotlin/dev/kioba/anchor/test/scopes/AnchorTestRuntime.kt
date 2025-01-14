package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalScope
import dev.kioba.anchor.SubscriptionScope
import dev.kioba.anchor.ViewState
import kotlin.coroutines.CoroutineContext

@PublishedApi
internal class AnchorTestRuntime<E, S>(
  @PublishedApi
  internal val effectScope: E,
  @PublishedApi
  internal val initState: S,
) : Anchor<E, S> where E : Effect, S : ViewState {

  val verifyActions = mutableListOf<VerifyAction>()
  var currentState = initState

  override val state: S
    get() = currentState

  override suspend fun post(
    block: SignalScope.() -> Signal
  ) {
    verifyActions.add(SignalAction { block(SignalScope) })
  }

  override suspend fun emit(
    block: SubscriptionScope.() -> Event,
  ) {
    verifyActions.add(EventAction { block(SubscriptionScope) })
  }

  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<E, S>.() -> Unit,
  ) {
    block()
  }

  override suspend fun <R> effect(
    coroutineContext: CoroutineContext,
    block: suspend E.() -> R,
  ): R =
    block(effectScope)

  override fun reduce(
    reducer: S.() -> S
  ) {
    verifyActions.add(ReducerAction(reducer))
    currentState = currentState.reducer()
  }

}
