package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.dsl.Anchor


@PublishedApi
internal fun interface AnchorChannel {
  fun execute(anchor: Anchor<out AnchorDslScope>)
}

@PublishedApi
internal val LocalAnchor: ProvidableCompositionLocal<AnchorChannel> =
  staticCompositionLocalOf {
    AnchorChannel {}
  }
