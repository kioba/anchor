package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlin.test.Test
import kotlin.test.assertEquals

private data class FxEffect(
  val data: String = "default",
) : Effect

private data class FxViewState(
  val result: String = "",
) : ViewState

private typealias FxAnchor = Anchor<FxEffect, FxViewState, Nothing>

private fun RememberAnchorScope.fxAnchor(): FxAnchor =
  create(
    initialState = ::FxViewState,
    effectScope = { FxEffect() },
  )

class EffectTest {

  /**
   * Verifies that `effect { }` in the action block executes against the
   * custom effect scope provided via `given { effectScope { } }`. The
   * returned value is used in a subsequent reduce to prove the custom
   * scope was active.
   */
  @Test
  fun effectReturnsValueFromCustomScope() =
    runAnchorTest(RememberAnchorScope::fxAnchor) {
      given("custom effect scope") {
        effectScope { FxEffect(data = "custom") }
      }

      on("reading data from effect") {
        val d = effect { data }
        reduce { copy(result = d) }
      }

      verify("result matches custom scope data") {
        assertState { copy(result = "custom") }
      }
    }

  /**
   * Verifies that when no effectScope override is provided, the factory's
   * default effect scope is used. FxEffect defaults to data = "default".
   */
  @Test
  fun effectUsesDefaultScope() =
    runAnchorTest(RememberAnchorScope::fxAnchor) {
      given("no override") {}

      on("reading data from default effect") {
        val d = effect { data }
        reduce { copy(result = d) }
      }

      verify("result matches factory default") {
        assertState { copy(result = "default") }
      }
    }

  /**
   * Verifies that `effect { }` calls are NOT recorded in verifyActions.
   * AnchorTestRuntime.effect() executes the block directly and returns
   * the result without adding to the action list. An action with only
   * an effect call and no reduce/post/emit produces zero recorded actions.
   */
  @Test
  fun effectDoesNotRecordAction() =
    runAnchorTest(RememberAnchorScope::fxAnchor) {
      given("default state") {}

      on("calling effect without reduce") { effect { data } }

      verify("no actions recorded") {}
    }

  /**
   * Verifies that effect execution can be validated indirectly by
   * asserting the state change that depends on the effect's return value.
   * Since `effect { }` is not recorded in verifyActions, the correctness
   * of the effect scope is proven through the reduce that uses its result.
   *
   * Note: `assertEffect` adds to expectedActions but does not consume from
   * actualActions, causing a size mismatch when mixed with other assertions.
   * The idiomatic way to verify effects is through their impact on state.
   */
  @Test
  fun effectVerifiedThroughStateImpact() =
    runAnchorTest(RememberAnchorScope::fxAnchor) {
      given("custom effect scope") {
        effectScope { FxEffect(data = "verify-me") }
      }

      on("reading from effect and reducing") {
        val d = effect { data }
        reduce { copy(result = d) }
      }

      verify("state proves effect returned the custom value") {
        assertState { copy(result = "verify-me") }
      }
    }

  /**
   * Verifies that multiple effects interspersed with reduces all execute
   * correctly. Each effect reads from the same scope, and each reduce
   * is captured and assertable in order.
   */
  @Test
  fun multipleEffectsAndReduces() =
    runAnchorTest(RememberAnchorScope::fxAnchor) {
      given("custom effect scope") {
        effectScope { FxEffect(data = "multi") }
      }

      on("two effects feeding two reduces") {
        val first = effect { data }
        reduce { copy(result = first) }
        val second = effect { "${data}-2" }
        reduce { copy(result = second) }
      }

      verify("both reduces captured in order") {
        assertState { copy(result = "multi") }
        assertState { copy(result = "multi-2") }
      }
    }
}
