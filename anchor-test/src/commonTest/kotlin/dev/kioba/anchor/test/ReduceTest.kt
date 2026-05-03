package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlin.test.Test
import kotlin.test.assertFailsWith

private class ReduceEffect : Effect

private data class ReduceViewState(
  val count: Int = 0,
  val name: String = "",
) : ViewState

private typealias ReduceAnchor = Anchor<ReduceEffect, ReduceViewState, Nothing>

private fun RememberAnchorScope.reduceAnchor(): ReduceAnchor =
  create(
    initialState = ::ReduceViewState,
    effectScope = { ReduceEffect() },
  )

class ReduceTest {

  /**
   * Verifies that a single reduce call is captured by the test runtime
   * and correctly matched by a single assertState. The assertion lambda
   * receives the initial state and must produce the expected new state.
   */
  @Test
  fun singleReduceUpdatesState() =
    runAnchorTest(RememberAnchorScope::reduceAnchor) {
      given("default state") {}

      on("reducing count to 1") { reduce { copy(count = 1) } }

      verify("state has count = 1") {
        assertState { copy(count = 1) }
      }
    }

  /**
   * Verifies state threading: each assertState lambda receives the state
   * that results from all previous reduces. The runningFold in assertEvents
   * carries state forward, so the second assertState sees count = 1 (not 0),
   * and the third sees count = 2.
   */
  @Test
  fun sequentialReducesThreadState() =
    runAnchorTest(RememberAnchorScope::reduceAnchor) {
      given("default state with count = 0") {}

      on("three sequential reduces building on each other") {
        reduce { copy(count = 1) }
        reduce { copy(count = count + 1) }
        reduce { copy(count = count * 10) }
      }

      verify("state threads through: 1 -> 2 -> 20") {
        assertState { copy(count = 1) }
        assertState { copy(count = count + 1) }
        assertState { copy(count = count * 10) }
      }
    }

  /**
   * Verifies that a reduce updating multiple fields at once is correctly
   * captured and asserted. Both count and name are set in a single reduce.
   */
  @Test
  fun reduceOnMultipleFields() =
    runAnchorTest(RememberAnchorScope::reduceAnchor) {
      given("default state") {}

      on("reducing two fields at once") {
        reduce { copy(count = 7, name = "hello") }
      }

      verify("both fields updated") {
        assertState { copy(count = 7, name = "hello") }
      }
    }

  /**
   * Verifies that an identity reducer (returning `this` unchanged) is still
   * recorded as an action and must be asserted. The framework does not
   * skip no-op reduces.
   */
  @Test
  fun reduceIdentityIsNoOp() =
    runAnchorTest(RememberAnchorScope::reduceAnchor) {
      given("default state") {}

      on("identity reduce") { reduce { this } }

      verify("identity reduce still must be asserted") {
        assertState { this }
      }
    }

  /**
   * Negative test: verifies that when assertState specifies a wrong
   * expected state, the framework throws an AssertionError. This confirms
   * the assertEquals check in the ReducerAction branch of assertEvents.
   */
  @Test
  fun assertStateMismatchFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::reduceAnchor) {
        given("default state") {}

        on("reducing count to 1") { reduce { copy(count = 1) } }

        verify("wrong expected value") {
          assertState { copy(count = 999) }
        }
      }
    }
  }

  /**
   * Negative test: verifies that having more assertState calls than actual
   * reduces causes a size mismatch AssertionError. The framework checks
   * expectedActions.size == actualActions.size before iterating.
   */
  @Test
  fun moreAssertsThanActionsFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::reduceAnchor) {
        given("default state") {}

        on("one reduce") { reduce { copy(count = 1) } }

        verify("two asserts for one reduce") {
          assertState { copy(count = 1) }
          assertState { copy(count = 2) }
        }
      }
    }
  }

  /**
   * Negative test: verifies that having fewer assertState calls than actual
   * reduces causes a size mismatch AssertionError. Every recorded action
   * must have a matching assertion.
   */
  @Test
  fun fewerAssertsThanActionsFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::reduceAnchor) {
        given("default state") {}

        on("two reduces") {
          reduce { copy(count = 1) }
          reduce { copy(count = 2) }
        }

        verify("only one assert for two reduces") {
          assertState { copy(count = 1) }
        }
      }
    }
  }
}
