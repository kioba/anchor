package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorRuntime
import dev.kioba.anchor.AnchorRuntimeScope
import dev.kioba.anchor.ContainedScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.UnitSignal
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.execute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

@Composable
public inline fun <reified S, E> ViewModelStoreOwner.RememberAnchor(
  noinline scope: @DisallowComposableCalls RememberAnchorScope.() -> Anchor<E, S>,
  customKey: String? = null,
  crossinline content: @Composable (S) -> Unit,
) where E : Effect, S : ViewState {
  val anchorScope: ContainedScope<AnchorRuntime<E, S>, E, S> =
    rememberViewModel(
      customKey ?: S::class.qualifiedName.orEmpty()
    ) { AnchorRuntimeScope.scope() as AnchorRuntime<E, S> }

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
    .map { SignalProvider { it } }
    .collectAsState(initial = SignalProvider { UnitSignal })

@Composable
@PublishedApi
internal inline fun <reified R, E, S> ContainedScope<R, E, S>.collectViewState(): State<S>
  where R : AnchorRuntime<E, S>, E : Effect, S : ViewState =
  anchor._viewState
    .collectAsState(Dispatchers.Main.immediate)
