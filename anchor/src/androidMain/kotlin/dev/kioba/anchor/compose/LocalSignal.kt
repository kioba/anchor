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

/**
 * CompositionLocal providing the stream of signals from the current Anchor.
 */
@PublishedApi
internal val LocalSignals: ProvidableCompositionLocal<Flow<SignalProvider>> =
  staticCompositionLocalOf { emptyFlow() }

/**
 * Handles one-time [Signal]s emitted by an Anchor.
 *
 * This Composable listens to the signal stream and executes the [block] when a signal of type [T] is received.
 * It uses [LaunchedEffect] to ensure the block is executed in a coroutine scope tied to the Composable's lifecycle.
 *
 * @param T The type of [Signal] to handle.
 * @param block The suspend function to execute when a signal of type [T] is received.
 *
 * Example:
 * ```kotlin
 * HandleSignal<CounterSignal.ShowError> { signal ->
 *   snackbarHostState.showSnackbar(signal.message)
 * }
 * ```
 */
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
