package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.main.data.MainAnchor
import dev.kioba.anchor.features.main.data.MainEffect
import dev.kioba.anchor.features.main.data.clear
import dev.kioba.anchor.features.main.data.mainAnchor
import dev.kioba.anchor.features.main.data.mainViewState
import dev.kioba.anchor.features.main.data.refresh
import dev.kioba.anchor.features.main.data.sayHi
import dev.kioba.anchor.features.main.model.MainEvent
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

class MainAnchorTest {
  @Test
  fun `initial effect sets the state details`() {
    runAnchorTest(RememberAnchorScope::mainAnchor) {
      on("initial setting the details", MainAnchor::sayHi)

      verify("the state updated with the specific details") {
        assertState { copy(details = "Hello Android!") }
      }
    }
  }

  @Test
  fun `clear cancels the counting`() {
    runAnchorTest(RememberAnchorScope::mainAnchor) {
      given("the initial state started to count up") {
        initialState { mainViewState().copy(hundreds = 100, iterationCounter = "100") }
      }

      on("setting the details page", MainAnchor::clear)

      verify("the state updated with the specific details") {
        assertEvent { MainEvent.Cancel }
        assertState { copy(details = "cleared", iterationCounter = null) }
      }
    }
  }

  @Test
  fun `refresh resets the counter`() {
    runAnchorTest(RememberAnchorScope::mainAnchor) {
      given("the initial state started to count up") {
        initialState { mainViewState() }
        effectScope { MainEffect() }
      }

      on("setting the details page", MainAnchor::refresh)

      verify("trigger refresh for subscriptions") {
        assertEvent { MainEvent.Refresh }
      }
    }
  }
}
