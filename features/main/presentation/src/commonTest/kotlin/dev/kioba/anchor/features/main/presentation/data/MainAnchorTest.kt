package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.features.main.presentation.model.MainEvent
import dev.kioba.anchor.features.main.presentation.model.MainViewState
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

class MainAnchorTest {
  @Test
  fun `initial effect sets the state details`() {
    runAnchorTest<MainEffect, MainViewState> {
      given("the initial state started") {
        initialState { mainViewState() }
        effectScope { MainEffect }
      }

      on("initial setting the details", ::sayHi)

      verify("the state updated with the specific details") {
        assertState { copy(details = "Hello Android!") }
      }
    }
  }

  @Test
  fun `clear cancels the counting`() {
    runAnchorTest<MainEffect, MainViewState> {
      given("the initial state started to count up") {
        initialState { mainViewState().copy(hundreds = 100, iterationCounter = "100") }
        effectScope { MainEffect }
      }

      on("setting the details page", ::clear)

      verify("the state updated with the specific details") {
        assertEvent { MainEvent.Cancel }
        assertState { copy(details = "cleared", iterationCounter = null) }
      }
    }
  }

  @Test
  fun `refresh resets the counter`() {
    runAnchorTest<MainEffect, MainViewState> {
      given("the initial state started to count up") {
        initialState { mainViewState() }
        effectScope { MainEffect }
      }

      on("setting the details page", ::refresh)

      verify("trigger refresh for subscriptions") {
        assertEvent { MainEvent.Refresh }
      }
    }
  }
}
