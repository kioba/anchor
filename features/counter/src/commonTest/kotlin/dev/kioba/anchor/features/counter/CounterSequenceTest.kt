package dev.kioba.anchor.features.counter

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.counter.data.CounterEffect
import dev.kioba.anchor.features.counter.data.CounterState
import dev.kioba.anchor.features.counter.data.counterAnchor
import dev.kioba.anchor.features.counter.data.decrement
import dev.kioba.anchor.features.counter.data.increment
import dev.kioba.anchor.features.counter.model.CounterSignal
import dev.kioba.anchor.test.runAnchorSequenceTest
import dev.kioba.anchor.test.runAnchorTest
import dev.kioba.anchor.test.scopes.AnchorStepScope
import kotlin.test.Test

// ── Composable step extensions ────────────────────────────────────────────────

private fun AnchorStepScope<CounterEffect, CounterState, Nothing>.incrementStep() {
  on("increment") { increment() }
  verify("count incremented") {
    assertState { copy(count = count.inc()) }
    assertSignal { CounterSignal.Increment }
  }
}

private fun AnchorStepScope<CounterEffect, CounterState, Nothing>.decrementStep() {
  on("decrement") { decrement() }
  verify("count decremented") {
    assertState { copy(count = count.dec()) }
    assertSignal { CounterSignal.Decrement }
  }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class CounterSequenceTest {

  /**
   * State threads across steps: increment from 5 → 6, then decrement from 6 → 5.
   * Proves the relative `copy(count = count.inc())` assertion is evaluated against
   * the state produced by the previous step, not the outer initial state.
   */
  @Test
  fun incrementThenDecrement_returnsToStart() =
    runAnchorSequenceTest(RememberAnchorScope::counterAnchor) {
      given("start at count 5") { initialState { CounterState(count = 5) } }

      step { incrementStep() }   // 5 → 6
      step { decrementStep() }   // 6 → 5
    }

  /**
   * Three inline increments inside a sequence thread state
   * without requiring a reusable step extension.
   */
  @Test
  fun threeIncrements_threadState() =
    runAnchorSequenceTest(RememberAnchorScope::counterAnchor) {
      given("start at count 0") { initialState { CounterState(count = 0) } }

      step("increment 1") {
        on("increment") { increment() }
        verify("count incremented") {
          assertState { copy(count = count.inc()) }   // 0 → 1
          assertSignal { CounterSignal.Increment }
        }
      }
      step("increment 2") {
        on("increment") { increment() }
        verify("count incremented") {
          assertState { copy(count = count.inc()) }   // 1 → 2
          assertSignal { CounterSignal.Increment }
        }
      }
      step("increment 3") {
        on("increment") { increment() }
        verify("count incremented") {
          assertState { copy(count = count.inc()) }   // 2 → 3
          assertSignal { CounterSignal.Increment }
        }
      }
    }

  /**
   * Two composable step extensions composed in sequence.
   * State flows 0 → 1 → 2 → 1, demonstrating reuse at arbitrary starting counts.
   */
  @Test
  fun composableStepsComposed() =
    runAnchorSequenceTest(RememberAnchorScope::counterAnchor) {
      given("start at count 0") { initialState { CounterState(count = 0) } }

      step { incrementStep() }   // 0 → 1
      step { incrementStep() }   // 1 → 2
      step { decrementStep() }   // 2 → 1
    }

  /**
   * Standalone single-action test for a single increment.
   */
  @Test
  fun incrementStep_standaloneWorks() =
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      given("initial counter state") { initialState { CounterState() } }
      on("increment") { increment() }
      verify("count incremented") {
        assertState { copy(count = count.inc()) }
        assertSignal { CounterSignal.Increment }
      }
    }
}
