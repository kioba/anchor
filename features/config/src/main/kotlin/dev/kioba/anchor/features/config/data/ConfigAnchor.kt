package dev.kioba.anchor.features.config.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.config.model.ConfigState

internal typealias ConfigEffect = EmptyEffect
internal typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState>

internal fun RememberAnchorScope.configAnchor(): ConfigAnchor =
  create(
    initialState = ::ConfigState,
    effectScope = { ConfigEffect },
  )
