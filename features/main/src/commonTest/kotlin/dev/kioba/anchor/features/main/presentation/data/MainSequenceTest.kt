package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.main.data.MainEffect
import dev.kioba.anchor.features.main.data.clear
import dev.kioba.anchor.features.main.data.dismissErrorDialog
import dev.kioba.anchor.features.main.data.iterationCounter
import dev.kioba.anchor.features.main.data.mainAnchor
import dev.kioba.anchor.features.main.data.mainViewState
import dev.kioba.anchor.features.main.data.sayHi
import dev.kioba.anchor.features.main.data.selectCounter
import dev.kioba.anchor.features.main.data.selectHome
import dev.kioba.anchor.features.main.data.selectQuack
import dev.kioba.anchor.features.main.data.triggerLocalError
import dev.kioba.anchor.features.main.data.updateCounterSelected
import dev.kioba.anchor.features.main.data.updateHomeSelected
import dev.kioba.anchor.features.main.data.updateProfileSelected
import dev.kioba.anchor.features.main.model.MainEvent
import dev.kioba.anchor.features.main.model.MainViewState
import dev.kioba.anchor.test.runAnchorSequenceTest
import dev.kioba.anchor.test.scopes.AnchorStepScope
import kotlin.test.Test

// ── Composable step extensions ────────────────────────────────────────────────

private fun AnchorStepScope<MainEffect, MainViewState, Nothing>.selectHomeStep() {
  on("select home") { selectHome() }
  verify("home tab selected") { assertState { updateHomeSelected() } }
}

private fun AnchorStepScope<MainEffect, MainViewState, Nothing>.selectCounterStep() {
  on("select counter") { selectCounter() }
  verify("counter tab selected") { assertState { updateCounterSelected() } }
}

private fun AnchorStepScope<MainEffect, MainViewState, Nothing>.selectConfigStep() {
  on("select config") { selectQuack() }
  verify("config tab selected") { assertState { updateProfileSelected() } }
}

// ── Tests ─────────────────────────────────────────────────────────────────────

class MainSequenceTest {

  /**
   * Tab selection threads state across three steps: each step's `assertState`
   * receiver is the state produced by the previous step, so assertions are
   * expressed as the same reducer functions used in production code.
   */
  @Test
  fun tabNavigation_threadsSelectedTab() =
    runAnchorSequenceTest(RememberAnchorScope::mainAnchor) {
      given("initial main view state") { initialState { mainViewState() } }

      step { selectCounterStep() }
      step { selectConfigStep() }
      step { selectHomeStep() }
    }

  /**
   * A locally caught error sets `errorDialog`; the following dismiss step
   * clears it. State threads between steps, so the dismiss step's receiver
   * already has the error message in `errorDialog`.
   */
  @Test
  fun localError_thenDismiss() =
    runAnchorSequenceTest(RememberAnchorScope::mainAnchor) {
      given("initial main view state") { initialState { mainViewState() } }

      step("trigger local error") {
        on("trigger local error") { triggerLocalError() }
        verify("error dialog set") {
          assertState { copy(errorDialog = "A locally caught error occurred.") }
        }
      }
      step("dismiss error dialog") {
        on("dismiss error dialog") { dismissErrorDialog() }
        verify("error dialog cleared") {
          assertState { copy(errorDialog = null) }
        }
      }
    }

  /**
   * Three actions in sequence covering `sayHi`, `iterationCounter`, and `clear`.
   * `iterationCounter` produces two sequential reducers — both must be asserted.
   * `clear` emits a `MainEvent.Cancel` before its state reducer, so the verify
   * block asserts the event first, then the state change.
   */
  @Test
  fun sayHi_thenIterationCounter_thenClear() =
    runAnchorSequenceTest(RememberAnchorScope::mainAnchor) {
      given("initial main view state") { initialState { mainViewState() } }

      step("say hi") {
        on("say hi") { sayHi() }
        verify("details updated") {
          assertState { copy(details = "Hello Android!") }
        }
      }
      step("iteration counter") {
        on("iteration counter 5") { iterationCounter(5) }
        verify("iteration results") {
          assertState { copy(details = "refreshed") }
          assertState { copy(iterationCounter = "5") }
        }
      }
      step("clear") {
        on("clear") { clear() }
        verify("cancelled and cleared") {
          assertEvent { MainEvent.Cancel }
          assertState { copy(details = "cleared", iterationCounter = null) }
        }
      }
    }
}
