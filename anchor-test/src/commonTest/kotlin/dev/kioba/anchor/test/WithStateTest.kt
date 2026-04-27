package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlin.test.Test

private class WsEffect : Effect

private data class WsViewState(
  val count: Int = 0,
  val doubled: Int = 0,
) : ViewState

private typealias WsAnchor = Anchor<WsEffect, WsViewState, Nothing>

private fun RememberAnchorScope.wsAnchor(): WsAnchor =
  create(
    initialState = ::WsViewState,
    effectScope = { WsEffect() },
  )

class WithStateTest {

  /**
   * Verifies that `withState` reads the current state. Given an initial
   * state with count = 5, `withState { count }` returns 5 which is then
   * used in a reduce to compute doubled = 10.
   */
  @Test
  fun withStateReadsInitialState() =
    runAnchorTest(RememberAnchorScope::wsAnchor) {
      given("initial count of 5") {
        initialState { WsViewState(count = 5) }
      }

      on("reading count via withState and doubling it") {
        val c = withState { count }
        reduce { copy(doubled = c * 2) }
      }

      verify("doubled is 10") {
        assertState { copy(doubled = 10) }
      }
    }

  /**
   * Verifies that `withState` after a reduce reads the updated state.
   * The first reduce sets count = 10, then `withState { count }` returns
   * 10, which is stored in doubled via the second reduce.
   */
  @Test
  fun withStateAfterReduceReadsUpdated() =
    runAnchorTest(RememberAnchorScope::wsAnchor) {
      given("default state") {}

      on("reduce then withState then reduce") {
        reduce { copy(count = 10) }
        val c = withState { count }
        reduce { copy(doubled = c) }
      }

      verify("withState saw the updated count") {
        assertState { copy(count = 10) }
        assertState { copy(doubled = 10) }
      }
    }

  /**
   * Verifies that `withState` alone does not emit any action. It is a
   * read-only operation that returns a value derived from the current
   * state without recording anything in verifyActions.
   */
  @Test
  fun withStateDoesNotEmitAction() =
    runAnchorTest(RememberAnchorScope::wsAnchor) {
      given("default state") {}

      on("only withState, no reduce") { withState { count } }

      verify("no actions recorded") {}
    }
}
