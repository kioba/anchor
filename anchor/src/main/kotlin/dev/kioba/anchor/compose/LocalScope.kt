package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.dsl.AnchorEffect


@PublishedApi
internal fun interface AnchorScopeDelegate {
  fun execute(anchorEffect: AnchorEffect<out AnchorDslScope>)
}

@PublishedApi
internal val LocalAnchor: ProvidableCompositionLocal<AnchorScopeDelegate> =
  staticCompositionLocalOf {
    AnchorScopeDelegate {}
  }
