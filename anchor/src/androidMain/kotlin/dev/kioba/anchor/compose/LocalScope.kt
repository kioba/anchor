package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorChannel

internal typealias AnchorChannelBase = AnchorChannel<Anchor<*, *>>

@Suppress("UNCHECKED_CAST")
@Composable
@ReadOnlyComposable
internal fun <A> ProvidableCompositionLocal<AnchorChannelBase>.currentTyped(): AnchorChannel<A>
  where A : Anchor<*, *> =
  current as (suspend A.() -> Unit) -> Unit

@PublishedApi
internal val LocalAnchor: ProvidableCompositionLocal<AnchorChannelBase> =
  staticCompositionLocalOf { {} }
