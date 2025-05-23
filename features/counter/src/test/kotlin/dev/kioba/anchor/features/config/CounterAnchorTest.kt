package dev.kioba.anchor.features.config

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.config.data.CounterAnchor
import dev.kioba.anchor.features.config.data.CounterState
import dev.kioba.anchor.features.config.data.counterAnchor
import dev.kioba.anchor.features.config.data.decrement
import dev.kioba.anchor.features.config.data.increment
import dev.kioba.anchor.features.config.model.CounterSignal
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

internal class CounterAnchorTest {
  @Test
  fun `counter increment updates state`() {
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      given("the screen started") {
        initialState { CounterState(count = -1) }
      }

      on("incrementing the counter", CounterAnchor::increment)

      verify("the state updated with the incremented value") {
        assertState { copy(count = 0) }
        assertSignal { CounterSignal.Increment }
      }
    }
  }

  @Test
  fun `counter decrement updates state`() {
    runAnchorTest(RememberAnchorScope::counterAnchor) {
      given("the screen started") {
        initialState { CounterState(count = 1) }
      }

      on("decrement the counter", CounterAnchor::decrement)

      verify("the state updated with the decremented value") {
        assertState { copy(count = 0) }
        assertSignal { CounterSignal.Decrement }
      }
    }
  }
}
