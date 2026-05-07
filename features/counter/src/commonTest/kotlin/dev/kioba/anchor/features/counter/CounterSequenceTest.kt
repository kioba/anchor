package dev.kioba.anchor.features.counter

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.counter.data.CounterEffect
import dev.kioba.anchor.features.counter.data.CounterState
import dev.kioba.anchor.features.counter.data.counterAnchor
import dev.kioba.anchor.features.counter.data.decrement
import dev.kioba.anchor.features.counter.data.increment
import dev.kioba.anchor.features.counter.model.CounterSignal
import dev.kioba.anchor.test.runAnchorTest
import dev.kioba.anchor.test.scopes.AnchorTestScope
import kotlin.test.Test

// ── Composable step extensions ────────────────────────────────────────────────

private fun AnchorTestScope<CounterEffect, CounterState, Nothing>.incrementStep() {
  given { initialState { CounterState() } }   // no-op inside sequence { }
  on { increment() }
  verify {
    assertState { copy(count = count.inc()) }
    assertSignal { CounterSignal.Increment }
  }
}

private fun AnchorTestScope<CounterEffect, CounterState, Nothing>.decrementStep() {
  given { initialState { CounterState() } }   // no-op inside sequence { }
  on { decrement() }
  verify {
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
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      given { initialState { CounterState(count = 5) } }

      sequence {
        step { incrementStep() }   // 5 → 6
        step { decrementStep() }   // 6 → 5
      }
    }

  /**
   * Three inline increments inside a single sequence block thread state
   * without requiring a reusable step extension.
   */
  @Test
  fun threeIncrements_threadState() =
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      given { initialState { CounterState(count = 0) } }

      sequence {
        step("increment 1") {
          on { increment() }
          verify {
            assertState { copy(count = count.inc()) }   // 0 → 1
            assertSignal { CounterSignal.Increment }
          }
        }
        step("increment 2") {
          on { increment() }
          verify {
            assertState { copy(count = count.inc()) }   // 1 → 2
            assertSignal { CounterSignal.Increment }
          }
        }
        step("increment 3") {
          on { increment() }
          verify {
            assertState { copy(count = count.inc()) }   // 2 → 3
            assertSignal { CounterSignal.Increment }
          }
        }
      }
    }

  /**
   * Two composable step extensions composed inside sequence { }.
   * State flows 0 → 1 → 2 → 1, demonstrating reuse at arbitrary starting counts.
   */
  @Test
  fun composableStepsComposed() =
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      given { initialState { CounterState(count = 0) } }

      sequence {
        step { incrementStep() }   // 0 → 1
        step { incrementStep() }   // 1 → 2
        step { decrementStep() }   // 2 → 1
      }
    }

  /**
   * The same composable step that works inside sequence { } also runs standalone.
   * Its own `initialState { CounterState() }` is live here (count = 0 → 1).
   */
  @Test
  fun incrementStep_standaloneWorks() =
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      incrementStep()
    }
}
