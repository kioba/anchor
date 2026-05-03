package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
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
   * block directly (AnchorTestRuntime line 52-53). There is no
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
}
