package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState

@PublishedApi
internal class VerifyScopeImpl<E, S>(
  @PublishedApi
  internal val expectedActions: MutableList<VerifyAction> = mutableListOf(),
) : VerifyScope<E, S> where E : Effect, S : ViewState {
  override fun assertState(
    f: S.() -> S,
  ) {
    expectedActions.add(ReducerAction(f))
  }

  override fun assertSignal(
    f: () -> Signal,
  ) {
    expectedActions.add(SignalAction(f))
  }

  override fun assertEvent(
    f: () -> Event,
  ) {
    expectedActions.add(EventAction(f))
  }

  override fun assertEffect(
    f: E.() -> Unit,
  ) {
    expectedActions.add(EffectAction(f))
  }
}

@PublishedApi
internal sealed interface VerifyAction

@PublishedApi
internal data class ReducerAction<S>(
  val reduce: S.() -> S,
) : VerifyAction

@PublishedApi
internal data class SignalAction(
  val signal: () -> Signal,
) : VerifyAction

@PublishedApi
internal data class EventAction(
  val event: () -> Event,
) : VerifyAction

@PublishedApi
internal data class EffectAction<E>(
  val effect: E.() -> Unit,
) : VerifyAction
