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

  /**
   * Records [f] as effect-scope setup, but nothing invokes it: neither
   * [dev.kioba.anchor.test.runAnchorTest] nor the outer `given` of
   * [dev.kioba.anchor.test.runAnchorSequenceTest] runs these blocks, so [f] has no effect on the
   * action. Configure the effect scope with [effectScope] instead. Inside a sequence `step`,
   * [StepGivenScope.effect] does run its block against the shared effect scope before that step's
   * action.
   */
  public suspend fun effect(
    f: suspend R.() -> Unit,
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
