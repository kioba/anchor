package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.scopes.AnchorStepScope
import kotlin.test.Test
import kotlin.test.assertFailsWith

// ── Test types ────────────────────────────────────────────────────────────────

private class SeqEffect(var fetchResult: String = "default") : Effect

private data class SeqState(
  val value: String = "",
  val count: Int = 0,
) : ViewState

private typealias SeqAnchor = Anchor<SeqEffect, SeqState, Nothing>

private fun RememberAnchorScope.seqAnchor(): SeqAnchor =
  create(
    initialState = ::SeqState,
    effectScope = { SeqEffect() },
  )

private suspend fun SeqAnchor.increment() {
  reduce { copy(count = count + 1) }
}

private suspend fun SeqAnchor.fetchAndSet() {
  val result = effect { fetchResult }
  reduce { copy(value = result) }
}

// ── Composable step extension ─────────────────────────────────────────────────

/**
 * Reusable step: state threads from the previous step so `copy(count = count + 1)`
 * always applies a relative delta regardless of the starting count.
 */
private fun AnchorStepScope<SeqEffect, SeqState, Nothing>.incrementStep() {
  on("increment") { increment() }
  verify("count incremented by one") {
    assertState { copy(count = count + 1) }
  }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class SequenceTest {

  /**
   * State threads between steps: step 2's assertState receiver is the
   * final state of step 1, not the outer initial state.
   */
  @Test
  fun sequenceThreadsStateBetweenSteps() =
    runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
      given("start at 0") { initialState { SeqState(count = 0) } }

      step("first increment") {
        on("increment") { increment() }
        verify("count is previous plus one") {
          assertState { copy(count = count + 1) }  // 0 + 1 = 1
        }
      }
      step("second increment") {
        on("increment") { increment() }
        verify("count is previous plus one") {
          assertState { copy(count = count + 1) }  // 1 + 1 = 2
        }
      }
    }

  /**
   * The outer `effectScope {}` creates the shared Effect instance used by all steps.
   * Both steps fetch from the same instance and see the same configured value.
   */
  @Test
  fun outerEffectScopeIsSharedAcrossAllSteps() =
    runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
      given("shared effect scope") { effectScope { SeqEffect(fetchResult = "shared") } }

      step("first fetch") {
        on("fetch and set") { fetchAndSet() }
        verify("value from shared effect") {
          assertState { copy(value = "shared") }
        }
      }
      step("second fetch") {
        on("fetch and set") { fetchAndSet() }
        verify("same effect scope reused") {
          assertState { copy(value = "shared") }
        }
      }
    }

  /**
   * `effect {}` in a step's `given {}` mutates the shared Effect instance's behaviour
   * for that step only. The Effect object itself is the same across all steps.
   */
  @Test
  fun stepEffectConfiguresMutableBehaviourPerStep() =
    runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
      given("base effect") { effectScope { SeqEffect() } }

      step("step returns step1") {
        given("configure step1 result") { effect { fetchResult = "step1" } }
        on("fetch and set") { fetchAndSet() }
        verify("value is step1") {
          assertState { copy(value = "step1") }
        }
      }
      step("step returns step2") {
        given("configure step2 result") { effect { fetchResult = "step2" } }
        on("fetch and set") { fetchAndSet() }
        verify("value is step2") {
          assertState { copy(value = "step2") }
        }
      }
    }

  /**
   * Two calls to the same composable step extension thread state correctly (0 → 1 → 2).
   */
  @Test
  fun composableStepExtensionComposesTwoIncrements() =
    runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
      given("start at 0") { initialState { SeqState(count = 0) } }

      step { incrementStep() }  // 0 → 1
      step { incrementStep() }  // 1 → 2
    }

  /**
   * The composable step works at any starting count because
   * assertState uses a relative delta `copy(count = count + 1)`.
   */
  @Test
  fun composableStepWorksAtAnyStartingCount() =
    runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
      given("start at 10") { initialState { SeqState(count = 10) } }

      step { incrementStep() }  // 10 → 11
    }

  /**
   * A standalone single-action test verifying an increment from count = 0.
   */
  @Test
  fun standaloneIncrementFromZero() =
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      given("start at 0") { initialState { SeqState(count = 0) } }
      on("increment") { increment() }
      verify("count goes to 1") { assertState { copy(count = count + 1) } }
    }

  /**
   * Calling on() twice without an intervening verify() inside a step
   * is a programming error and throws an IllegalStateException immediately.
   */
  @Test
  fun onCalledTwiceWithoutVerifyThrows() {
    assertFailsWith<IllegalStateException> {
      runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
        step("on called twice throws") {
          on("first") { increment() }
          on("second without verify") { increment() }  // throws
          verify("unreachable") { assertState { copy(count = count + 1) } }
        }
      }
    }
  }
}
