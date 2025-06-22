package dev.kioba.anchor.features.counter.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

public suspend fun ConfigAnchor.updateText(
  text: String,
) {
  withContext(Dispatchers.Default) {
    delay(3000)
    reduce { copy(text = text) }
  }
}
