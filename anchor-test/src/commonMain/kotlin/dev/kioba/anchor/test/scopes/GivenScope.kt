package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
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
    f: suspend ErrorScope<R, S>.(Err) -> Unit,
  )

  public fun defect(
    f: suspend ErrorScope<R, S>.(Throwable) -> Unit,
  )
}
