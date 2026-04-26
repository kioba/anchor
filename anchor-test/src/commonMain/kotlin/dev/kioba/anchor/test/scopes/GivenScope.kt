package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface GivenScope<R : Effect, S : ViewState, Err : Any> {
  public fun initialState(
    f: () -> S,
  )

  public suspend fun effect(
    f: R.() -> Unit,
  )

  public suspend fun effectScope(
    f: () -> R,
  )

  public fun onDomainError(
    f: suspend Anchor<R, S, Err>.(Err) -> Unit,
  )

  public fun defect(
    f: suspend Anchor<R, S, Err>.(Throwable) -> Unit,
  )
}
