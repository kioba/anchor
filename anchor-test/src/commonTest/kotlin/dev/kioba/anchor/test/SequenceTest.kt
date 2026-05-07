package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.scopes.AnchorTestScope
import kotlin.test.Test
import kotlin.test.assertFailsWith

// ── Test types ────────────────────────────────────────────────────────────────

private interface SeqApi {
  suspend fun fetch(): String
}

private class SeqEffect(val api: SeqApi = object : SeqApi {
  override suspend fun fetch() = "default"
}) : Effect

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
  val result = effect { api.fetch() }
  reduce { copy(value = result) }
}

// ── Composable step extension (defined once, reusable) ───────────────────────

/**
 * Standalone: initialState is live — starts at whatever the outer given provides.
 * In sequence: initialState is a no-op — state threads from the previous step.
 * In both cases the assertion is relative: `copy(count = count + 1)`.
 */
private fun AnchorTestScope<SeqEffect, SeqState, Nothing>.incrementStep() {
  given {
    initialState { SeqState(count = 0) }
  }
  on("increment") { increment() }
  verify {
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
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      given("start at 0") { initialState { SeqState(count = 0) } }

      sequence {
        on("first increment") { increment() }
        verify {
          assertState { copy(count = count + 1) }  // 0 + 1 = 1
        }
        on("second increment") { increment() }
        verify {
          assertState { copy(count = count + 1) }  // 1 + 1 = 2
        }
      }
    }

  /**
   * `initialState` inside a step's given block is a no-op in sequence mode.
   * The outer given's initialState (count = 5) drives the starting state.
   */
  @Test
  fun sequenceIgnoresStepLevelInitialState() =
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      given("start at 5") { initialState { SeqState(count = 5) } }

      sequence {
        given {
          initialState { SeqState(count = 0) }  // ignored — count stays 5
        }
        on("increment") { increment() }
        verify {
          assertState { copy(count = count + 1) }  // 5 + 1 = 6
        }
      }
    }

  /**
   * Each step can supply its own effect scope override. The override applies
   * only to that step; the next step is unaffected.
   */
  @Test
  fun sequenceAppliesStepLevelEffectScope() =
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      given("empty state") { initialState { SeqState() } }

      sequence {
        given {
          effectScope { SeqEffect(api = object : SeqApi { override suspend fun fetch() = "step1" }) }
        }
        on("fetch") { fetchAndSet() }
        verify {
          assertState { copy(value = "step1") }
        }

        given {
          effectScope { SeqEffect(api = object : SeqApi { override suspend fun fetch() = "step2" }) }
        }
        on("fetch") { fetchAndSet() }
        verify {
          assertState { copy(value = "step2") }
        }
      }
    }

  /**
   * An extension function on AnchorTestScope acts as a composable step:
   * it can be called inside sequence {} just like any other DSL block.
   * Two calls to incrementStep() thread state correctly (0 → 1 → 2).
   */
  @Test
  fun composableStepExtensionComposesTwoIncrements() =
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      given("start at 0") { initialState { SeqState(count = 0) } }

      sequence {
        incrementStep()  // 0 → 1
        incrementStep()  // 1 → 2
      }
    }

  /**
   * The same composable step works at any starting count because
   * assertState uses a relative delta `copy(count = count + 1)`.
   */
  @Test
  fun composableStepWorksAtAnyStartingCount() =
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      given("start at 10") { initialState { SeqState(count = 10) } }

      sequence {
        incrementStep()  // 10 → 11
      }
    }

  /**
   * A standalone test that uses the same composable step extension
   * in single-step mode (no sequence). Verifies that the step's own
   * initialState is live in this context.
   */
  @Test
  fun composableStepRunsStandaloneWithItsOwnInitialState() =
    runAnchorTest(RememberAnchorScope::seqAnchor) {
      incrementStep()  // initialState { SeqState(count = 0) } is live → 0 → 1
    }

  /**
   * Calling on() twice without an intervening verify() inside sequence
   * is a programming error and throws an IllegalStateException immediately.
   */
  @Test
  fun sequenceOnCalledTwiceWithoutVerifyThrows() {
    assertFailsWith<IllegalStateException> {
      runAnchorTest(RememberAnchorScope::seqAnchor) {
        given("default") {}
        sequence {
          on("first") { increment() }
          on("second without verify") { increment() }  // throws
          verify { assertState { copy(count = count + 1) } }
        }
      }
    }
  }
}
