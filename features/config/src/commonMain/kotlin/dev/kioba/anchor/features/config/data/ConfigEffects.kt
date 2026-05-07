package dev.kioba.anchor.features.config.data

import dev.kioba.anchor.Recover
import dev.kioba.anchor.features.config.model.ConfigError
import dev.kioba.anchor.recover
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

public suspend fun ConfigAnchor.updateText(text: String) {
  ensure(text.isNotBlank()) { ConfigError.EmptyInput }
  ensure(text.length <= 100) { ConfigError.TooLong(maxLength = 100) }
  withContext(Dispatchers.Default) {
    delay(1000)
    reduce { copy(text = text.trim(), errorMessage = null) }
  }
}

public suspend fun ConfigAnchor.updateTextClamped(text: String) {
  val result = recover {
    ensure(text.isNotBlank()) { ConfigError.EmptyInput }
    ensure(text.length <= 100) { ConfigError.TooLong(maxLength = 100) }
    text.trim()
  }
  val validated: String = when (result) {
    is Recover.Ok -> result.value
    is Recover.Error -> when (val err = result.error) {
      is ConfigError.TooLong -> text.take(100).trim()
      ConfigError.EmptyInput -> return
    }
  }
  withContext(Dispatchers.Default) {
    delay(1000)
    reduce { copy(text = validated, errorMessage = null) }
  }
}
