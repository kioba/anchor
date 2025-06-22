package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.UnitSignal

@PublishedApi
internal val LocalSignals: ProvidableCompositionLocal<SignalProvider> =
  staticCompositionLocalOf {
    SignalProvider { UnitSignal }
  }

@Composable
public inline fun <reified T> HandleSignal(
  noinline f: suspend (T) -> Unit,
) {
  val signalProvider = LocalSignals.current
  when (val signal = signalProvider.provide()) {
    is UnitSignal -> Unit
    is T -> LaunchedEffect(signalProvider) { f(signal) }
  }
}
