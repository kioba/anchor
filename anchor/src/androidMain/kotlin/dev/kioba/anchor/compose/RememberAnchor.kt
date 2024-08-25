package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.UnitSignal
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.flow.map

@Composable
public inline fun <reified C, reified S, E> ViewModelStoreOwner.RememberAnchor(
  noinline scope: @DisallowComposableCalls () -> C,
  customKey: String? = null,
  crossinline content: @Composable C.(S) -> Unit,
) where
        C : Anchor<E, S>, E : Effect, S : ViewState {
  val anchorScope = rememberViewModel(customKey ?: S::class.qualifiedName!!, scope)

  val state by anchorScope.collectViewState()
  val signal by anchorScope.collectSignal()
  val delegate = rememberUpdatedState(newValue = anchorScope.actionChannel)

  CompositionLocalProvider(
    LocalSignals provides signal,
    LocalAnchor provides delegate.value,
    content = { anchorScope.anchor.content(state) },
  )
}

@Composable
@PublishedApi
internal inline fun <reified R, E, S> ContainedScope<R, E, S>.collectSignal(): State<SignalProvider>
  where R : Anchor<E, S>, E : Effect, S : ViewState =
  anchor.signals
    .map { SignalProvider { it } }
    .collectAsState(initial = SignalProvider { UnitSignal })

@Composable
@PublishedApi
internal inline fun <reified A, E, S> ContainedScope<A, E, S>.collectViewState(): State<S>
  where A : Anchor<E, S>, E : Effect, S : ViewState =
  anchor._viewState
    .collectAsState()
