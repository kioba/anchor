package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import kotlin.test.Test
import kotlin.test.assertFailsWith

private data class OrdEffect(
  val data: String = "test",
) : Effect

private data class OrdViewState(
  val a: Int = 0,
  val b: Int = 0,
) : ViewState

private sealed interface OrdSignal : Signal {
  data object A : OrdSignal
  data object B : OrdSignal
}

private sealed interface OrdEvent : Event {
  data object X : OrdEvent
  data object Y : OrdEvent
}

private typealias OrdAnchor = Anchor<OrdEffect, OrdViewState, Nothing>

private fun RememberAnchorScope.ordAnchor(): OrdAnchor =
  create(
    initialState = ::OrdViewState,
    effectScope = { OrdEffect() },
  )

class ActionOrderingTest {

  /**
   * Verifies that a complex sequence of interleaved reduces, signals,
   * and events is recorded in exact execution order. Each assertion
   * type must match the corresponding action at that position. This
   * tests the ordered iteration in assertEvents.
   */
  @Test
  fun interleavedReduceSignalEvent() =
    runAnchorTest(RememberAnchorScope::ordAnchor) {
      given("default state") {}

      on("reduce, signal, event, reduce, signal") {
        reduce { copy(a = 1) }
        post { OrdSignal.A }
        emit { OrdEvent.X }
        reduce { copy(b = 2) }
        post { OrdSignal.B }
      }

      verify("all five actions in exact order") {
        assertState { copy(a = 1) }
        assertSignal { OrdSignal.A }
        assertEvent { OrdEvent.X }
        assertState { copy(b = 2) }
        assertSignal { OrdSignal.B }
      }
    }

  /**
   * Negative test: verifies that swapping two assertion types (signal
   * and event) causes an AssertionError. The assertIs check in
   * assertEvents detects the type mismatch when a SignalAction is
   * expected but an EventAction is found (or vice versa).
   */
  @Test
  fun wrongAssertionOrderFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::ordAnchor) {
        given("default state") {}

        on("signal then event") {
          post { OrdSignal.A }
          emit { OrdEvent.X }
        }

        verify("swapped: event then signal") {
          assertEvent { OrdEvent.X }
          assertSignal { OrdSignal.A }
        }
      }
    }
  }

  /**
   * Negative test: verifies that having more assertions than recorded
   * actions causes a size mismatch AssertionError. The assertEquals
   * on sizes fires before any individual action matching.
   */
  @Test
  fun extraAssertionsFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::ordAnchor) {
        given("default state") {}

        on("one reduce") { reduce { copy(a = 1) } }

        verify("two assertions for one action") {
          assertState { copy(a = 1) }
          assertSignal { OrdSignal.A }
        }
      }
    }
  }

  /**
   * Negative test: verifies that having fewer assertions than recorded
   * actions causes a size mismatch AssertionError. Every recorded
   * action must have a matching assertion.
   */
  @Test
  fun missingAssertionsFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::ordAnchor) {
        given("default state") {}

        on("two actions") {
          reduce { copy(a = 1) }
          post { OrdSignal.A }
        }

        verify("only one assertion") {
          assertState { copy(a = 1) }
        }
      }
    }
  }

  /**
   * Verifies that all action types (reduce, signal, event, effect, reduce)
   * can appear in a single test. Effects are not recorded in verifyActions
   * (they execute directly), so only non-effect actions need assertions.
   */
  @Test
  fun allActionTypesInOneTest() =
    runAnchorTest(RememberAnchorScope::ordAnchor) {
      given("custom effect scope") {
        effectScope { OrdEffect(data = "all-types") }
      }

      on("reduce, effect, signal, event, reduce") {
        reduce { copy(a = 1) }
        effect { data }
        post { OrdSignal.A }
        emit { OrdEvent.X }
        reduce { copy(b = 2) }
      }

      verify("all action types verified") {
        assertState { copy(a = 1) }
        assertSignal { OrdSignal.A }
        assertEvent { OrdEvent.X }
        assertState { copy(b = 2) }
      }
    }
}
