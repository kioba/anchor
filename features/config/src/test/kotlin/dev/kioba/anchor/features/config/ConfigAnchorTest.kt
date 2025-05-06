package dev.kioba.anchor.features.config

import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.config.data.configAnchor
import dev.kioba.anchor.features.config.data.updateText
import dev.kioba.anchor.features.config.model.ConfigState
import dev.kioba.anchor.test.runAnchorTest
import kotlin.test.Test

internal class ConfigAnchorTest {
  @Test
  fun `config updateText update state`() {
    val text = "Hello"
    runAnchorTest(RememberAnchorScope::configAnchor) {
      given("the screen started") {
        initialState { ConfigState() }
      }

      on("updating the text with delay") {
        updateText(text)
      }

      verify("the state updated with the text value") {
        assertState { copy(text = text) }
      }
    }
  }
}
