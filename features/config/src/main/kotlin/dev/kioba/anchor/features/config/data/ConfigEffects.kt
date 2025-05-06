package dev.kioba.anchor.features.config.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal suspend fun ConfigAnchor.updateText(
  text: String,
) {
  withContext(Dispatchers.Default) {
    delay(3000)
    reduce { copy(text = text) }
  }
}
