package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@AnchorTestDsl
public interface VerifyScope<R, S, Err> where R : Effect, S : ViewState, Err : Any {
  public fun assertState(
    f: S.() -> S,
  )

  public fun assertSignal(
    f: () -> Signal,
  )

  public fun assertEvent(
    f: () -> Event,
  )

  public fun assertEffect(
    f: R.() -> Unit,
  )

  /**
   * Asserts that [dev.kioba.anchor.Raise.raise] was called with the given error.
   *
   * @param f A block that returns the expected error value.
   */
  public fun assertRaise(
    f: () -> Err,
  )

  /**
   * Asserts that [dev.kioba.anchor.orDie] was called with the given error.
   *
   * @param f A block that returns the expected error value.
   */
  public fun assertOrDie(
    f: () -> Err,
  )

  /**
   * Asserts that the `onDomainError` handler was invoked with the given error.
   *
   * @param f A block that returns the expected error value.
   */
  public fun assertDomainError(
    f: () -> Err,
  )

  /**
   * Asserts that the `defect` handler was invoked with the given throwable.
   *
   * @param f A block that returns the expected throwable.
   */
  public fun assertDefect(
    f: () -> Throwable,
  )
}
