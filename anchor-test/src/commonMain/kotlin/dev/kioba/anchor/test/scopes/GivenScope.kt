package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface GivenScope<R : Effect, S : ViewState, Err : Any> {
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

  @AnchorTestDsl
  public fun onDomainError(
    f: suspend Anchor<R, S, Err>.(Err) -> Unit,
  )

  @AnchorTestDsl
  public fun defect(
    f: suspend Anchor<R, S, Err>.(Throwable) -> Unit,
  )
}
