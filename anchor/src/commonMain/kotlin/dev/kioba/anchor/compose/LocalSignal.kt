package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.Signal
import dev.kioba.anchor.internal.SignalEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter

@PublishedApi
internal val LocalSignals: ProvidableCompositionLocal<Flow<SignalEvent>> =
  staticCompositionLocalOf {
    emptyFlow()
  }

@Composable
public inline fun <reified T : Signal> HandleSignal(
  noinline f: suspend (T) -> Unit,
) {
  val signals = LocalSignals.current
  LaunchedEffect(signals) {
    var lastProcessedId: Long? = null
    signals.filter { it.signal is T }.collect { event ->
      if (event.id != lastProcessedId) {
        f(event.signal as T)
        lastProcessedId = event.id
      }
    }
  }
}
