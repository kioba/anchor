package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.ActionChannel

@PublishedApi
internal val LocalAnchor: ProvidableCompositionLocal<ActionChannel> =
  staticCompositionLocalOf {
    ActionChannel { }
  }
