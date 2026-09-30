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
   * Runs [f] against the effect scope before the action, so a test can seed or adjust it in place,
   * for example through a suspending setter on a fake.
   *
   * - The blocks run after the effect scope is resolved (the [effectScope] override if there is
   *   one, otherwise the factory's), in declaration order. A block declared before [effectScope]
   *   still applies to the override.
   * - They run in the test coroutine, so `delay` uses virtual time.
   * - In the outer `given` of [dev.kioba.anchor.test.runAnchorSequenceTest] they run once, before
   *   the first step, against the effect scope that every step shares. Use
   *   [StepGivenScope.effect] for changes before a single step.
   * - A block that throws fails the test with that exception. It reaches neither `onDomainError`
   *   nor `defect`.
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
