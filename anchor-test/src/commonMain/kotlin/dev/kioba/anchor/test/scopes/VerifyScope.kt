package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface VerifyScope<R, S, Err> where R : Effect, S : ViewState, Err : Any {
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
    f: R.() -> Unit,
  )

  /**
   * Asserts that [raise] was called with the given error.
   *
   * @param f A block that returns the expected error value.
   */
  @AnchorTestDsl
  public fun assertRaise(
    f: () -> Err,
  )

  /**
   * Asserts that [orDie] was called with the given error.
   *
   * @param f A block that returns the expected error value.
   */
  @AnchorTestDsl
  public fun assertOrDie(
    f: () -> Err,
  )

  /**
   * Asserts that the `onDomainError` handler was invoked with the given error.
   *
   * @param f A block that returns the expected error value.
   */
  @AnchorTestDsl
  public fun assertDomainError(
    f: () -> Err,
  )

  /**
   * Asserts that no domain error occurred during the action.
   */
  @AnchorTestDsl
  public fun assertNoDomainError()

  /**
   * Asserts that the `defect` handler was invoked with the given throwable.
   *
   * @param f A block that returns the expected throwable.
   */
  @AnchorTestDsl
  public fun assertDefect(
    f: () -> Throwable,
  )
}
