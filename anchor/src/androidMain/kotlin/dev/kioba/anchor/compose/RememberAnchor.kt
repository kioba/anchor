package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorRuntimeScope
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * AnchorCompositionScope provides access to the state and actions of an Anchor.
 */
public interface AnchorStateScope<S : ViewState> {
  /**
   * Returns the current state. Accessing this property will cause the calling
   * composable to recompose whenever any part of the state changes.
   */
  public val state: S
    @Composable get

  /**
   * Collects a specific part of the state. Recomposes only when the selected
   * value changes.
   */
  @Composable
  public fun <T> collectState(
    selector: (S) -> T,
  ): T
}

@PublishedApi
internal class AnchorStateScopeImpl<S : ViewState>(
  private val stateFlow: StateFlow<S>,
) : AnchorStateScope<S> {
  override val state: S
    @Composable get() = stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate).value

  @Composable
  override fun <T> collectState(
    selector: (S) -> T,
  ): T {
    val updatedSelector = rememberUpdatedState(selector)
    val state by stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    return remember(state, updatedSelector) { updatedSelector.value(state) }
  }
}

@Suppress("ModifierRequired")
@Composable
public fun <S : ViewState> PreviewAnchor(
  state: S,
  content: @Composable AnchorStateScope<S>.() -> Unit,
) {
  AnchorStateScopeImpl(stateFlow = MutableStateFlow(state)).content()
}

/**
 * Sets up Anchor state management within a Composable, providing automatic lifecycle management
 * and state retention across configuration changes.
 *
 * This composable integrates Anchor with Jetpack Compose by:
 * Creating or retrieving a ViewModel-scoped Anchor instance
 * Providing signal handling capabilities
 * Making action functions available via [anchor]
 * The Anchor instance is retained across configuration changes (like screen rotation) through
 * ViewModel integration, ensuring your state persists throughout the component lifecycle.
 * Use [AnchorStateScope.collectState] to observe only the necessary parts of the state.
 *
 * @param S The ViewState type representing your UI state
 * @param E The Effect type providing dependencies for side effects
 * @param scope Factory function that creates the Anchor instance. Called only once per ViewModel.
 * @param customKey Optional key for ViewModel storage. Defaults to the qualified name of S.
 *        Use this when you need multiple instances of the same state type in the same scope.
 * @param content Composable content that receives the [AnchorStateScope].
 *
 * @sample
 * ```kotlin
 * @Composable
 * fun CounterScreen() {
 *   RememberAnchor(scope = { counterAnchor() }) {
 *     Column {
 *       val count = collectState { it.count }
 *       Text("Count: $count")
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
@Suppress("ModifierRequired")
@Composable
public inline fun <reified S, E> RememberAnchor(
  noinline scope: @DisallowComposableCalls RememberAnchorScope.() -> Anchor<E, S>,
  customKey: String? = null,
  crossinline content: @Composable AnchorStateScope<S>.() -> Unit,
) where E : Effect, S : ViewState {
  val key = customKey ?: S::class.qualifiedName.orEmpty()

  val anchorScope: ContainerViewModel<E, S> =
    viewModel(
      key = key,
      factory = containerViewModelFactory { AnchorRuntimeScope.scope() as AnchorRuntime<E, S> },
    )

  val stateFlow = remember(anchorScope) { anchorScope.anchor.viewState }
  val signalFlow = remember(anchorScope) { anchorScope.anchor.signals }
  val compositionScope = remember(stateFlow) { AnchorStateScopeImpl(stateFlow) }

  CompositionLocalProvider(
    LocalSignals provides signalFlow,
    LocalAnchor provides AnchorScope(anchorScope),
    content = { compositionScope.content() },
  )
}
