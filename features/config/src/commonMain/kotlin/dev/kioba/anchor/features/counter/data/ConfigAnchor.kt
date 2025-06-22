package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.features.counter.model.ConfigState

public class ConfigEffect(): Effect
internal typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState>

public fun RememberAnchorScope.configAnchor(): ConfigAnchor =
  create(
    initialState = ::ConfigState,
    effectScope = { ConfigEffect() },
  )
