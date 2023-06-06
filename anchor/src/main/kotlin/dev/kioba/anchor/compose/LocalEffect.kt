package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.dsl.AnchorSignal
import dev.kioba.anchor.dsl.UnitSignal

@PublishedApi
internal fun interface SignalProvider {
  fun provide(): AnchorSignal
}

@PublishedApi
internal val LocalSignals: ProvidableCompositionLocal<SignalProvider> =
  staticCompositionLocalOf { SignalProvider { UnitSignal } }

@Composable
public inline fun <reified T> HandleCommand(
  noinline f: suspend (T) -> Unit,
) {
  val signalProvider = LocalSignals.current
  when (val effect = signalProvider.provide()) {
    is UnitSignal -> Unit
    is T -> LaunchedEffect(signalProvider) { f(effect) }
  }
}