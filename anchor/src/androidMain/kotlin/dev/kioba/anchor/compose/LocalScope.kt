package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorChannel


@PublishedApi
internal val LocalAnchor: ProvidableCompositionLocal<AnchorChannel> =
  staticCompositionLocalOf {
    AnchorChannel {}
  }
