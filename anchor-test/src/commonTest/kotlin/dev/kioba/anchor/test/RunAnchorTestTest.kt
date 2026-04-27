package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlin.test.Test

private class RunEffect : Effect

private data class RunViewState(
  val value: Int = 0,
) : ViewState

private typealias RunAnchor = Anchor<RunEffect, RunViewState, Nothing>

private fun RememberAnchorScope.runAnchor(): RunAnchor =
  create(
    initialState = ::RunViewState,
    effectScope = { RunEffect() },
  )

class RunAnchorTestTest {

  /**
   * Verifies that the minimal BDD structure (empty given/on/verify) compiles
   * and completes without errors. This is the simplest possible test using
   * the DSL — it exercises the full lifecycle with zero actions and zero
   * assertions.
   */
  @Test
  fun minimalTestRunsWithoutAssertions() =
    runAnchorTest(RememberAnchorScope::runAnchor) {
      given("no setup") {}

      on("doing nothing") {}

      verify("nothing to check") {}
    }

  /**
   * Verifies that when no `initialState` is provided in the given block,
   * the anchor factory's own default state is used. The action reduces
   * from the factory default (value = 0) to value = 1, confirming the
   * default was applied.
   */
  @Test
  fun defaultStateFromFactory() =
    runAnchorTest(RememberAnchorScope::runAnchor) {
      given("factory defaults") {}

      on("reducing from default state") { reduce { copy(value = value + 1) } }

      verify("state starts from factory default of 0") {
        assertState { copy(value = value + 1) }
      }
    }

  /**
   * Verifies the simplest happy path: one reduce in the action maps to
   * exactly one assertState in the verify block. This confirms the basic
   * wiring between action recording and assertion matching.
   */
  @Test
  fun actionReduceMatchesSingleAssert() =
    runAnchorTest(RememberAnchorScope::runAnchor) {
      given("default state") {}

      on("a single reduce") { reduce { copy(value = 42) } }

      verify("one assertState matches") {
        assertState { copy(value = 42) }
      }
    }

  /**
   * Verifies that multiple reduces in a single action are all recorded
   * and must each be matched by an assertState call. This tests the
   * size-equality check in [AnchorTestScope.assertEvents] which ensures
   * expectedActions.size == actualActions.size.
   */
  @Test
  fun multipleReducesMatchMultipleAsserts() =
    runAnchorTest(RememberAnchorScope::runAnchor) {
      given("default state") {}

      on("three sequential reduces") {
        reduce { copy(value = 1) }
        reduce { copy(value = 2) }
        reduce { copy(value = 3) }
      }

      verify("three assertState calls match in order") {
        assertState { copy(value = 1) }
        assertState { copy(value = 2) }
        assertState { copy(value = 3) }
      }
    }
}
