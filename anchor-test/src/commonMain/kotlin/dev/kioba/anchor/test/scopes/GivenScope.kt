package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface GivenScope<E : Effect, S : ViewState> {
  @AnchorTestDsl
  public fun initialState(
    f: () -> S,
  )

  @AnchorTestDsl
  public suspend fun effect(
    f: E.() -> Unit,
  )

  @AnchorTestDsl
  public suspend fun effectScope(
    f: () -> E,
  )
}
