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
 * The signals of the current Anchor, filtered per collector so each [HandleSignal] claims only the held signals of
 * its own type.
 */
@PublishedApi
internal fun interface SignalSource {
  fun signalsMatching(
    accepts: (Signal) -> Boolean,
  ): Flow<SignalProvider>
}

/**
 * CompositionLocal providing the [SignalSource] of the current Anchor.
 */
@PublishedApi
internal val LocalSignals: ProvidableCompositionLocal<SignalSource> =
  staticCompositionLocalOf { SignalSource { emptyFlow() } }

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
 * Delivery guarantees: a posted signal is delivered to every collector that is attached and accepts it at the time
 * of posting; a [HandleSignal] accepts the signals of type [T]. If no attached collector accepts it, the signal is
 * held, up to 64 signals, dropping the oldest, and is delivered once to the first accepting collector that attaches
 * afterwards. Delivered signals are never replayed. A signal already handed to a collector that is cancelled before
 * processing it is lost.
 *
 * So a signal posted from `init` before the first composition collects, while the lifecycle is below STARTED, or
 * during a configuration change reaches the first [HandleSignal] for its type once that one collects. Held signals
 * live in memory only: process death loses them, so model outcomes that must survive it in state.
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
  val source = LocalSignals.current
  val update = rememberUpdatedState(block)
  val lifecycleOwner = LocalLifecycleOwner.current
  // Collect the stream directly. Reducing it to a latest-value State would conflate bursts, and keying the
  // effect on that value would cancel a running handler whenever the next signal arrives.
  LaunchedEffect(source, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      // Filtered at the source, so this handler claims only held signals of type T and leaves the rest held.
      source.signalsMatching { it is T }.collect { provider ->
        val signal = provider.provide()
        if (signal is T) {
          update.value(signal)
        }
      }
    }
  }
}
