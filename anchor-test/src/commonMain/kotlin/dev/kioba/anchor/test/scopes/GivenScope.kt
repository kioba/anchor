package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface GivenScope<R : Effect, S : ViewState> {
  @AnchorTestDsl
  public fun initialState(
    f: () -> S,
  )

  @AnchorTestDsl
  public suspend fun effect(
    f: R.() -> Unit,
  )

  @AnchorTestDsl
  public suspend fun effectScope(
    f: () -> R,
  )
}
