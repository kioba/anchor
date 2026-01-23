package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorRuntimeScope
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.ContainedScope
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory
import kotlinx.coroutines.Dispatchers

/**
 * Sets up Anchor state management within a Composable, providing automatic lifecycle management
 * and state retention across configuration changes.
 *
 * This version is optimized for performance as it does not collect the full state by default.
 * Use [AnchorCompositionScope.collectState] to observe only the necessary parts of the state.
 *
 * @param S The ViewState type representing your UI state
 * @param E The Effect type providing dependencies for side effects
 * @param scope Factory function that creates the Anchor instance. Called only once per ViewModel.
 * @param customKey Optional key for ViewModel storage. Defaults to the qualified name of S.
 * @param content Composable content that receives the [AnchorCompositionScope].
 */
@Composable
public inline fun <reified S, E> RememberAnchor(
  noinline scope: @DisallowComposableCalls RememberAnchorScope.() -> Anchor<E, S>,
  customKey: String? = null,
  crossinline content: @Composable AnchorCompositionScope<S>.() -> Unit,
) where E : Effect, S : ViewState {
  val key = customKey ?: S::class.qualifiedName.orEmpty()

  val anchorScope: ContainerViewModel<E, S> =
    viewModel(
      key = key,
      factory = containerViewModelFactory { AnchorRuntimeScope.scope() as AnchorRuntime<E, S> }
    )

  val stateFlow = anchorScope.anchor.viewState
  val compositionScope = remember(stateFlow) { AnchorCompositionScopeImpl(stateFlow) }

  CompositionLocalProvider(
    LocalSignals provides anchorScope.anchor.signalEvents,
    LocalAnchor provides AnchorScope(anchorScope),
    content = { compositionScope.content() },
  )
}

/**
 * Sets up Anchor state management within a Composable.
 *
 * This version passes the state directly to the content block for convenience,
 * but will recompose the entire content block on every state update.
 */
@Composable
public inline fun <reified S, E> RememberAnchor(
  noinline scope: @DisallowComposableCalls RememberAnchorScope.() -> Anchor<E, S>,
  customKey: String? = null,
  crossinline content: @Composable AnchorCompositionScope<S>.(S) -> Unit,
) where E : Effect, S : ViewState {
  val key = customKey ?: S::class.qualifiedName.orEmpty()

  val anchorScope: ContainerViewModel<E, S> =
    viewModel(
      key = key,
      factory = containerViewModelFactory { AnchorRuntimeScope.scope() as AnchorRuntime<E, S> }
    )

  val stateFlow = anchorScope.anchor.viewState
  val state by stateFlow.collectAsState(Dispatchers.Main.immediate)
  val compositionScope = remember(stateFlow) { AnchorCompositionScopeImpl(stateFlow) }

  CompositionLocalProvider(
    LocalSignals provides anchorScope.anchor.signalEvents,
    LocalAnchor provides AnchorScope(anchorScope),
    content = { compositionScope.content(state) },
  )
}

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
