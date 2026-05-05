package dev.kioba.anchor.features.config.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.config.model.ConfigError
import dev.kioba.anchor.features.config.model.ConfigState

public class ConfigEffect : Effect

internal typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState, ConfigError>

public fun RememberAnchorScope.configAnchor(): ConfigAnchor =
  create(
    initialState = ::ConfigState,
    effectScope = { ConfigEffect() },
    onDomainError = { error ->
      when (error) {
        ConfigError.EmptyInput ->
          reduce { copy(errorMessage = "Text cannot be empty") }
        is ConfigError.TooLong ->
          reduce { copy(errorMessage = "Text exceeds ${error.maxLength} characters") }
      }
    },
    defect = { t ->
      reduce { copy(errorMessage = "Unexpected error: ${t.message}") }
    },
  )
