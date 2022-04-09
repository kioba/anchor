package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.AnchorSyntax
import dev.kioba.anchor.dsl.Action


@PublishedApi
internal fun interface ActionDelegate {
  fun execute(action: Action<out AnchorSyntax>)
}

@PublishedApi
internal val LocalScope: ProvidableCompositionLocal<ActionDelegate> =
  staticCompositionLocalOf {
    error("Could not find an ActionDelegate provider")
  }
