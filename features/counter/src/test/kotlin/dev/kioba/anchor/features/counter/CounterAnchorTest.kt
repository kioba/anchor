package dev.kioba.anchor.features.counter

import dev.kioba.anchor.features.counter.data.CounterEffect
import dev.kioba.anchor.features.counter.data.CounterState
import dev.kioba.anchor.features.counter.data.decrement
import dev.kioba.anchor.features.counter.data.increment
import dev.kioba.anchor.features.counter.model.CounterSignal
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

internal class CounterAnchorTest {
  @Test
  fun `counter increment updates state`() {
    runAnchorTest<CounterEffect, CounterState> {
      given("the screen started") {
        initialState { CounterState(count = 0) }
        effectScope { CounterEffect }
      }

      on("incrementing the counter", ::increment)

      verify("the state updated with the incremented value") {
        assertState { copy(count = 1) }
        assertSignal { CounterSignal.Increment }
      }
    }
  }

  @Test
  fun `counter decrement updates state`() {
    runAnchorTest<CounterEffect, CounterState> {
      given("the screen started") {
        initialState { CounterState(count = 1) }
        effectScope { CounterEffect }
      }

      on("decrement the counter", ::decrement)

      verify("the state updated with the decremented value") {
        assertState { copy(count = 0) }
        assertSignal { CounterSignal.Decrement }
      }
    }
  }
}
