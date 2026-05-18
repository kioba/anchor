package dev.kioba.anchor.features.config

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.config.data.configAnchor
import dev.kioba.anchor.features.config.data.updateText
import dev.kioba.anchor.features.config.model.ConfigState
import dev.kioba.anchor.test.runAnchorSequenceTest
import kotlin.test.Test

class ConfigSequenceTest {

  /**
   * Two sequential `updateText` calls thread state correctly:
   * the second step's `assertState` receiver is `ConfigState(text = "hello")`,
   * so `copy(text = "world")` verifies the final text without hardcoding the full state.
   */
  @Test
  fun updateText_thenUpdateAgain_threadsState() =
    runAnchorSequenceTest(RememberAnchorScope::configAnchor) {
      given("initial config state") { initialState { ConfigState() } }

      step("update to hello") {
        on("update text") { updateText("hello") }
        verify("text updated") {
          assertState { copy(text = "hello") }
        }
      }
      step("update to world") {
        on("update text") { updateText("world") }
        verify("text updated") {
          assertState { copy(text = "world") }
        }
      }
    }

  /**
   * Updating to the same text value twice still records both reducers.
   * The framework never skips a reduce even when the resulting state is identical.
   */
  @Test
  fun updateToSameValue_recordsBothReducers() =
    runAnchorSequenceTest(RememberAnchorScope::configAnchor) {
      given("initial config state") { initialState { ConfigState() } }

      step("update to same first time") {
        on("update text") { updateText("same") }
        verify("text updated") {
          assertState { copy(text = "same") }
        }
      }
      step("update to same second time") {
        on("update text") { updateText("same") }
        verify("text updated") {
          assertState { copy(text = "same") }
        }
      }
    }
}
