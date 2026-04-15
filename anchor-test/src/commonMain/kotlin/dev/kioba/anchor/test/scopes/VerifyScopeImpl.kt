package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState

@PublishedApi
internal class VerifyScopeImpl<R, S, Err>(
  @PublishedApi
  internal val expectedActions: MutableList<VerifyAction> = mutableListOf(),
) : VerifyScope<R, S, Err> where R : Effect, S : ViewState, Err : Any {
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
    f: R.() -> Unit,
  ) {
    expectedActions.add(EffectAction(f))
  }

  override fun assertRaise(
    f: () -> Err,
  ) {
    expectedActions.add(RaiseAction(f()))
  }

  override fun assertOrDie(
    f: () -> Err,
  ) {
    expectedActions.add(OrDieAction(f()))
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
internal data class EffectAction<R>(
  val effect: R.() -> Unit,
) : VerifyAction

@PublishedApi
internal data class RaiseAction(
  val error: Any,
) : VerifyAction

@PublishedApi
internal data class OrDieAction(
  val error: Any,
) : VerifyAction
