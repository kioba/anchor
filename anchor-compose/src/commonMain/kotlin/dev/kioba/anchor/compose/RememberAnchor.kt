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
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.anchorContainerViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Provides access to the [ViewState] and utilities for observing it within a Composable.
 *
 * This scope is available within the `content` block of [RememberAnchor].
 *
 * @param S The [ViewState] type.
 */
public interface AnchorStateScope<S : ViewState> {
  /**
   * The current [ViewState].
   *
   * Accessing this property will cause the calling Composable to recompose whenever any part of the state changes.
   * For more granular observation, use [collectState].
   */
  public val state: S
    @Composable get

  /**
   * Collects a specific part of the [ViewState].
   *
   * Recomposes only when the selected value (returned by the [selector]) changes.
   *
   * @param T The type of the selected value.
   * @param selector A function that maps the [ViewState] to the desired value.
   * @return The current value returned by the [selector].
   *
   * Example:
   * ```kotlin
   * val count = collectState { it.count }
   * ```
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

/**
 * A utility Composable for previewing UI that uses Anchor.
 *
 * It provides a static [state] to the [content] block, simulating a [RememberAnchor] environment.
 *
 * @param S The [ViewState] type.
 * @param state The static state to use for the preview.
 * @param content The Composable content to preview.
 *
 * Example:
 * ```kotlin
 * @Preview
 * @Composable
 * fun CounterPreview() {
 *   PreviewAnchor(state = CounterState(count = 10)) {
 *     CounterContent()
 *   }
 * }
 * ```
 */
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
 * This Composable integrates Anchor with Compose Multiplatform by:
 * 1. Creating or retrieving a ViewModel-scoped Anchor instance.
 * 2. Providing signal handling capabilities via [LocalSignals].
 * 3. Making action functions available via [LocalAnchor] (used by the [anchor] helper).
 *
 * The Anchor instance is retained across configuration changes (like screen rotation) through
 * ViewModel integration, ensuring your state persists throughout the component lifecycle.
 *
 * @param S The [ViewState] type representing your UI state.
 * @param E The [Effect] type providing dependencies for side effects.
 * @param scope Factory function that creates the [Anchor] instance. Called only once per ViewModel.
 * @param customKey Optional key for ViewModel storage. Defaults to the qualified name of [S].
 *        Use this when you need multiple instances of the same state type in the same scope.
 * @param content Composable content that receives the [AnchorStateScope].
 *
 * Example:
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
 * @see anchor For creating action callbacks.
 * @see HandleSignal For handling one-time signals.
 * @see dev.kioba.anchor.Anchor For the core state management interface.
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
      factory = anchorContainerViewModelFactory { scope() },
    )

  val stateFlow = remember(anchorScope) { anchorScope.viewState }
  val signalFlow = remember(anchorScope) { anchorScope.signals }
  val compositionScope = remember(stateFlow) { AnchorStateScopeImpl(stateFlow) }

  CompositionLocalProvider(
    LocalSignals provides signalFlow,
    LocalAnchor provides anchorScope,
    content = { compositionScope.content() },
  )
}
