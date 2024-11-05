package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface VerifyScope<E, S> where E : Effect, S : ViewState {
  @AnchorTestDsl
  public fun assertState(
    f: S.() -> S,
  )

  @AnchorTestDsl
  public fun assertSignal(
    f: () -> Signal,
  )

  @AnchorTestDsl
  public fun assertEvent(
    f: () -> Event,
  )

  @AnchorTestDsl
  public fun assertEffect(
    f: E.() -> Unit,
  )
}
