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
import dev.kioba.anchor.ContainedScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.UnitSignal
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.execute
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory
import kotlinx.coroutines.Dispatchers

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
    LocalAnchor provides anchorScope::execute,
    content = { content(state) },
  )
}

@Composable
@PublishedApi
internal inline fun <reified R, E, S> ContainedScope<R, E, S>.collectSignal(): State<SignalProvider>
  where R : AnchorRuntime<E, S>, E : Effect, S : ViewState =
  anchor.signals
    .collectAsState(initial = SignalProvider { UnitSignal }, Dispatchers.Main.immediate)

@Composable
@PublishedApi
internal inline fun <reified R, E, S> ContainedScope<R, E, S>.collectViewState(): State<S>
  where R : AnchorRuntime<E, S>, E : Effect, S : ViewState =
  anchor._viewState
    .collectAsState(Dispatchers.Main.immediate)
