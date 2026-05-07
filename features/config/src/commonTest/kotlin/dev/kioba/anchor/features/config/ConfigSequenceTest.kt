package dev.kioba.anchor.features.config

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.counter.data.configAnchor
import dev.kioba.anchor.features.counter.data.updateText
import dev.kioba.anchor.features.counter.model.ConfigState
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

class ConfigSequenceTest {

  /**
   * Two sequential `updateText` calls thread state correctly:
   * the second step's `assertState` receiver is `ConfigState(text = "hello")`,
   * so `copy(text = "world")` verifies the final text without hardcoding the full state.
   */
  @Test
  fun updateText_thenUpdateAgain_threadsState() =
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given { initialState { ConfigState() } }

      sequence {
        step("update to hello") {
          on { updateText("hello") }
          verify {
            assertState { copy(text = "hello") }
          }
        }
        step("update to world") {
          on { updateText("world") }
          verify {
            assertState { copy(text = "world") }
          }
        }
      }
    }

  /**
   * Updating to the same text value twice still records both reducers.
   * The framework never skips a reduce even when the resulting state is identical.
   */
  @Test
  fun updateToSameValue_recordsBothReducers() =
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given { initialState { ConfigState() } }

      sequence {
        step("update to same first time") {
          on { updateText("same") }
          verify {
            assertState { copy(text = "same") }
          }
        }
        step("update to same second time") {
          on { updateText("same") }
          verify {
            assertState { copy(text = "same") }
          }
        }
      }
    }
}
