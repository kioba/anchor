package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.test.Test

private class CancelEffect : Effect

private data class CancelViewState(
  val value: Int = 0,
) : ViewState

private sealed interface CancelSignal : Signal {
  data object Done : CancelSignal
}

private typealias CancelAnchor = Anchor<CancelEffect, CancelViewState, Nothing>

private fun RememberAnchorScope.cancelAnchor(): CancelAnchor =
  create(
    initialState = ::CancelViewState,
    effectScope = { CancelEffect() },
  )

class CancellableTest {

  /**
   * Verifies that `cancellable` in the test runtime simply runs the
   * block directly (`AnchorTestRuntime.cancellable`). There is no
   * cancellation semantics in tests — the block executes inline.
   * The reduce inside is captured normally.
   */
  @Test
  fun cancellableRunsBlockDirectly() =
    runAnchorTest(RememberAnchorScope::cancelAnchor) {
      given("default state") {}

      on("reducing inside cancellable") {
        cancellable("key") {
          reduce { copy(value = 1) }
        }
      }

      verify("reduce was captured") {
        assertState { copy(value = 1) }
      }
    }

  /**
   * Verifies that mixed actions (reduce + signal) inside a cancellable
   * block are all captured in order. The cancellable wrapper is
   * transparent to action recording.
   */
  @Test
  fun cancellableWithMixedActions() =
    runAnchorTest(RememberAnchorScope::cancelAnchor) {
      given("default state") {}

      on("reduce and signal inside cancellable") {
        cancellable("key") {
          reduce { copy(value = 1) }
          post { CancelSignal.Done }
        }
      }

      verify("both actions captured in order") {
        assertState { copy(value = 1) }
        assertSignal { CancelSignal.Done }
      }
    }

  /**
   * Verifies that two separate cancellable blocks with different keys
   * both execute fully. In the test runtime, cancellable does not
   * cancel previous jobs — each block runs to completion.
   */
  @Test
  fun nestedCancellableBlocks() =
    runAnchorTest(RememberAnchorScope::cancelAnchor) {
      given("default state") {}

      on("two cancellable blocks with different keys") {
        cancellable("first") {
          reduce { copy(value = 1) }
        }
        cancellable("second") {
          reduce { copy(value = value + 10) }
        }
      }

      verify("both blocks ran") {
        assertState { copy(value = 1) }
        assertState { copy(value = value + 10) }
      }
    }

  /**
   * Pins the documented divergence on `AnchorTestRuntime.cancellable`: a
   * second block with the same key does NOT cancel a block that is still
   * running. The first block is suspended in `delay` when the second one
   * starts; the production runtime would cancel it before its reduce, but
   * here it resumes and its reduce is recorded after the second block's.
   */
  @Test
  fun sameKeyDoesNotCancelInFlightBlock() =
    runAnchorTest(RememberAnchorScope::cancelAnchor) {
      given("default state") {}

      on("two overlapping cancellable blocks with the same key") {
        coroutineScope {
          launch {
            cancellable("key") {
              delay(100)
              reduce { copy(value = 1) }
            }
          }
          launch {
            cancellable("key") {
              reduce { copy(value = 2) }
            }
          }
        }
      }

      verify("the superseded block still ran to completion") {
        assertState { copy(value = 2) }
        assertState { copy(value = 1) }
      }
    }
}
