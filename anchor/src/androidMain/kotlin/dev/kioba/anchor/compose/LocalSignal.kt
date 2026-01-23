package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

@PublishedApi
internal val LocalSignals: ProvidableCompositionLocal<Flow<SignalProvider>> =
  staticCompositionLocalOf { emptyFlow() }

@Suppress("ModifierRequired")
@Composable
public inline fun <reified T : Signal> HandleSignal(
  noinline block: @DisallowComposableCalls suspend (T) -> Unit,
) {
  val signals by LocalSignals.current.collectAsStateWithLifecycle(null)
  val update = rememberUpdatedState(block)
  LaunchedEffect(signals) {
    val signal = signals?.provide()
    if (signal is T) {
      update.value(signal)
    }
  }
}
