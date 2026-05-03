package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlin.test.Test
import kotlin.test.assertFailsWith

private class EvtEffect : Effect

private data class EvtViewState(
  val value: Int = 0,
) : ViewState

private sealed interface EvtEvent : Event {
  data object Refresh : EvtEvent
  data object Cancel : EvtEvent
}

private typealias EvtAnchor = Anchor<EvtEffect, EvtViewState, Nothing>

private fun RememberAnchorScope.evtAnchor(): EvtAnchor =
  create(
    initialState = ::EvtViewState,
    effectScope = { EvtEffect() },
  )

class EventTest {

  /**
   * Verifies that a single event emitted via `emit { }` is captured by
   * the test runtime and correctly matched by assertEvent. Events are
   * compared by value equality.
   */
  @Test
  fun singleEventEmitted() =
    runAnchorTest(RememberAnchorScope::evtAnchor) {
      given("default state") {}

      on("emitting Refresh") { emit { EvtEvent.Refresh } }

      verify("Refresh event was recorded") {
        assertEvent { EvtEvent.Refresh }
      }
    }

  /**
   * Verifies that multiple events emitted in sequence are recorded in
   * order and must be asserted in the same order. Order is enforced by
   * the sequential removal from actualActions in assertEvents.
   */
  @Test
  fun multipleEventsInOrder() =
    runAnchorTest(RememberAnchorScope::evtAnchor) {
      given("default state") {}

      on("emitting Refresh then Cancel") {
        emit { EvtEvent.Refresh }
        emit { EvtEvent.Cancel }
      }

      verify("events match in emitted order") {
        assertEvent { EvtEvent.Refresh }
        assertEvent { EvtEvent.Cancel }
      }
    }

  /**
   * Negative test: verifies that asserting the wrong event type causes
   * an AssertionError. The assertEquals in the EventAction branch of
   * assertEvents detects the mismatch.
   */
  @Test
  fun eventMismatchFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::evtAnchor) {
        given("default state") {}

        on("emitting Refresh") { emit { EvtEvent.Refresh } }

        verify("wrong event asserted") {
          assertEvent { EvtEvent.Cancel }
        }
      }
    }
  }

  /**
   * Verifies that events can be interleaved with reduces and both are
   * recorded in correct order. Events do not affect state threading —
   * the second assertState receives the state produced by the first reduce.
   */
  @Test
  fun eventInterleavedWithReduce() =
    runAnchorTest(RememberAnchorScope::evtAnchor) {
      given("default state") {}

      on("reduce, event, reduce") {
        reduce { copy(value = 1) }
        emit { EvtEvent.Refresh }
        reduce { copy(value = value + 1) }
      }

      verify("actions match in order, state threads across event") {
        assertState { copy(value = 1) }
        assertEvent { EvtEvent.Refresh }
        assertState { copy(value = value + 1) }
      }
    }
}
