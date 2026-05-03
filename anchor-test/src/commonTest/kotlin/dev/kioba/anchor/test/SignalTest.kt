package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import kotlin.test.Test
import kotlin.test.assertFailsWith

private class SigEffect : Effect

private data class SigViewState(
  val value: Int = 0,
) : ViewState

private sealed interface SigSignal : Signal {
  data object Ping : SigSignal
  data object Pong : SigSignal
}

private typealias SigAnchor = Anchor<SigEffect, SigViewState, Nothing>

private fun RememberAnchorScope.sigAnchor(): SigAnchor =
  create(
    initialState = ::SigViewState,
    effectScope = { SigEffect() },
  )

class SignalTest {

  /**
   * Verifies that a single signal posted via `post { }` is captured by
   * the test runtime and correctly matched by assertSignal. The signal
   * is compared by value equality.
   */
  @Test
  fun singleSignalPosted() =
    runAnchorTest(RememberAnchorScope::sigAnchor) {
      given("default state") {}

      on("posting a Ping signal") { post { SigSignal.Ping } }

      verify("Ping signal was recorded") {
        assertSignal { SigSignal.Ping }
      }
    }

  /**
   * Verifies that multiple signals posted in sequence are recorded in
   * order and must be asserted in the same order. Swapping the order
   * in verify would fail.
   */
  @Test
  fun multipleSignalsInOrder() =
    runAnchorTest(RememberAnchorScope::sigAnchor) {
      given("default state") {}

      on("posting Ping then Pong") {
        post { SigSignal.Ping }
        post { SigSignal.Pong }
      }

      verify("signals match in posted order") {
        assertSignal { SigSignal.Ping }
        assertSignal { SigSignal.Pong }
      }
    }

  /**
   * Negative test: verifies that asserting the wrong signal type causes
   * an AssertionError. The assertEquals in the SignalAction branch of
   * assertEvents detects the mismatch.
   */
  @Test
  fun signalMismatchFails() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::sigAnchor) {
        given("default state") {}

        on("posting Ping") { post { SigSignal.Ping } }

        verify("wrong signal asserted") {
          assertSignal { SigSignal.Pong }
        }
      }
    }
  }

  /**
   * Verifies that signals can be interleaved with reduces and both are
   * recorded in the correct order. The assertState/assertSignal/assertState
   * sequence must exactly match the reduce/post/reduce action sequence.
   */
  @Test
  fun signalInterleavedWithReduce() =
    runAnchorTest(RememberAnchorScope::sigAnchor) {
      given("default state") {}

      on("reduce, signal, reduce") {
        reduce { copy(value = 1) }
        post { SigSignal.Ping }
        reduce { copy(value = 2) }
      }

      verify("actions match in order") {
        assertState { copy(value = 1) }
        assertSignal { SigSignal.Ping }
        assertState { copy(value = 2) }
      }
    }
}
