package dev.kioba.anchor.features.config

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.config.data.configAnchor
import dev.kioba.anchor.features.config.data.updateText
import dev.kioba.anchor.features.config.data.updateTextClamped
import dev.kioba.anchor.features.config.model.ConfigError
import dev.kioba.anchor.features.config.model.ConfigState
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

internal class ConfigAnchorTest {

  @Test
  fun `updateText with valid input updates state`() {
    val text = "Hello"
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given("initial state") {
        initialState { ConfigState() }
      }

      on("updating with valid text") { updateText(text) }

      verify("state updated with text, no error") {
        assertState { copy(text = text) }
      }
    }
  }

  @Test
  fun `updateText with empty input raises EmptyInput domain error`() {
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given("initial state") {
        initialState { ConfigState() }
      }

      on("updating with blank text") { updateText("") }

      verify("EmptyInput domain error handled and state reflects error message") {
        assertRaise { ConfigError.EmptyInput }
        assertState { copy(errorMessage = "Text cannot be empty") }
        assertDomainError { ConfigError.EmptyInput }
      }
    }
  }

  @Test
  fun `updateText with text over limit raises TooLong domain error`() {
    val longText = "a".repeat(101)
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given("initial state") {
        initialState { ConfigState() }
      }

      on("updating with text over 100 chars") { updateText(longText) }

      verify("TooLong domain error handled and state reflects error message") {
        assertRaise { ConfigError.TooLong(maxLength = 100) }
        assertState { copy(errorMessage = "Text exceeds 100 characters") }
        assertDomainError { ConfigError.TooLong(maxLength = 100) }
      }
    }
  }

  @Test
  fun `updateTextClamped silently truncates instead of propagating TooLong`() {
    val longText = "a".repeat(101)
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given("initial state") {
        initialState { ConfigState() }
      }

      on("updating clamped with text over 100 chars") { updateTextClamped(longText) }

      verify("error caught locally, state updated with truncated text") {
        assertRaise { ConfigError.TooLong(maxLength = 100) }
        assertState { copy(text = "a".repeat(100)) }
      }
    }
  }
}
