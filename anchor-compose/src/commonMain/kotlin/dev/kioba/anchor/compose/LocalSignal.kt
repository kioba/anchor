package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
 * Collects the signal stream of the enclosing [RememberAnchor] and calls [block] for every signal of
 * type [T]. Signals of other types are skipped.
 *
 * Delivery:
 * - Every signal is handled, in the order it was posted. A burst of signals is not conflated to the latest one.
 * - Signals are handled one at a time. While [block] suspends (for example on `showSnackbar`), later signals
 *   wait in the Anchor's signal buffer instead of cancelling it. The buffer holds 64 signals; once it is full,
 *   `post` suspends until the handler catches up.
 * - Collection runs while the [LocalLifecycleOwner]'s lifecycle is at least [Lifecycle.State.STARTED]. When it
 *   falls below STARTED, collection stops, and a [block] that is still running is cancelled and not run again.
 *   Collection starts again when the lifecycle is back at STARTED. It also stops when [HandleSignal] leaves the
 *   composition.
 * - The latest [block] is used for every signal. Passing a new lambda does not restart collection.
 *
 * Known limitation: a signal reaches only the handlers that are collecting when it is posted. A signal posted
 * while no handler is collecting is dropped, not buffered: for example from `init` before the first composition
 * starts collecting, while the lifecycle is below STARTED, or during a configuration change. Model outcomes
 * that must not be missed in state.
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
  val signals = LocalSignals.current
  val update = rememberUpdatedState(block)
  val lifecycleOwner = LocalLifecycleOwner.current
  // Collect the stream directly. Reducing it to a latest-value State would conflate bursts, and keying the
  // effect on that value would cancel a running handler whenever the next signal arrives.
  LaunchedEffect(signals, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      signals.collect { provider ->
        val signal = provider.provide()
        if (signal is T) {
          update.value(signal)
        }
      }
    }
  }
}
