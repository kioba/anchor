package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorRuntime
import dev.kioba.anchor.AnchorRuntimeScope
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.ContainedScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.UnitSignal
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory
import kotlinx.coroutines.Dispatchers

/**
 * Sets up Anchor state management within a Composable, providing automatic lifecycle management
 * and state retention across configuration changes.
 *
 * This composable integrates Anchor with Jetpack Compose by:
 * - Creating or retrieving a ViewModel-scoped Anchor instance
 * - Collecting state updates and triggering recomposition
 * - Providing signal handling capabilities
 * - Making action functions available via [anchor]
 *
 * The Anchor instance is retained across configuration changes (like screen rotation) through
 * ViewModel integration, ensuring your state persists throughout the component lifecycle.
 *
 * @param S The ViewState type representing your UI state
 * @param E The Effect type providing dependencies for side effects
 * @param scope Factory function that creates the Anchor instance. Called only once per ViewModel.
 * @param customKey Optional key for ViewModel storage. Defaults to the qualified name of S.
 *        Use this when you need multiple instances of the same state type in the same scope.
 * @param content Composable content that receives the current state and can use [anchor] to
 *        create action callbacks and [HandleSignal] to handle one-time events.
 *
 * @sample
 * ```kotlin
 * @Composable
 * fun CounterScreen() {
 *   RememberAnchor(scope = { counterAnchor() }) { state ->
 *     Column {
 *       Text("Count: ${state.count}")
 *       Button(onClick = anchor(CounterAnchor::increment)) {
 *         Text("Increment")
 *       }
 *       HandleSignal<CounterSignal> { signal ->
 *         when (signal) {
 *           CounterSignal.Increment -> showSnackbar("Incremented!")
 *         }
 *       }
 *     }
 *   }
 * }
 * ```
 *
 * @see anchor For creating action callbacks
 * @see HandleSignal For handling one-time events
 * @see dev.kioba.anchor.Anchor For the core state management interface
 */
@Composable
public inline fun <reified S, E> RememberAnchor(
  noinline scope: @DisallowComposableCalls RememberAnchorScope.() -> Anchor<E, S>,
  customKey: String? = null,
  crossinline content: @Composable (S) -> Unit,
) where E : Effect, S : ViewState {
  val key = customKey ?: S::class.qualifiedName.orEmpty()

  val anchorScope: ContainerViewModel<E, S> =
    viewModel(
      key = key,
      factory = containerViewModelFactory { AnchorRuntimeScope.scope() as AnchorRuntime<E, S> }
    )

  val state by anchorScope.collectViewState()
  val signal by anchorScope.collectSignal()

  CompositionLocalProvider(
    LocalSignals provides signal,
    LocalAnchor provides AnchorScope(anchorScope),
    content = { content(state) },
  )
}

/**
 * Collects signal emissions from the Anchor as Compose State.
 * Signals are collected on the Main.immediate dispatcher to ensure timely UI updates.
 */
@Composable
@PublishedApi
internal inline fun <reified R, E, S> ContainedScope<R, E, S>.collectSignal(): State<SignalProvider>
  where R : AnchorRuntime<E, S>, E : Effect, S : ViewState =
  anchor.signals
    .collectAsState(initial = SignalProvider { UnitSignal }, Dispatchers.Main.immediate)

/**
 * Collects view state from the Anchor as Compose State.
 * State updates are collected on the Main.immediate dispatcher for synchronous recomposition.
 */
@Composable
@PublishedApi
internal inline fun <reified R, E, S> ContainedScope<R, E, S>.collectViewState(): State<S>
  where R : AnchorRuntime<E, S>, E : Effect, S : ViewState =
  anchor._viewState
    .collectAsState(Dispatchers.Main.immediate)
