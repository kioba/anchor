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

  /**
   * Adds an expected entry that no recorded action can match. Despite its name, it does not check
   * that the action called `effect { }`: the test runtime does not record effect calls.
   *
   * Verification first requires as many expected entries as recorded actions, then matches the
   * other assertions against the recorded actions in order. This entry consumes none of them, so:
   *
   * - Next to assertions that match every recorded action, it fails the count check (for example
   *   `expected:<2> but was:<1>`).
   * - It passes only when each `assertEffect` leaves one recorded action unasserted. Those
   *   trailing recorded actions are then never checked.
   *
   * When the count check passes, [f] is invoked in its turn with the effect scope, after the action
   * has run.
   * To verify an effect, assert what the action did with its result instead, for example with
   * [assertState].
   */
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
