package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.dsl.UnitSignal
import kotlinx.coroutines.flow.map

context (ViewModelStoreOwner)
  @Composable
  public inline fun <reified C, reified S, E> RememberAnchor(
  noinline scope: @DisallowComposableCalls () -> C,
  customKey: String? = null,
  crossinline content: @Composable (S) -> Unit,
) where
  C : AnchorScope<S, E> {
  val anchorScope = rememberViewModel(customKey ?: S::class.qualifiedName!!, scope)

  val state by anchorScope.collectViewState()
  val signal by anchorScope.collectEffects()
  val delegate = rememberUpdatedState(newValue = anchorScope.actionChannel)

  CompositionLocalProvider(
    LocalSignals provides signal,
    LocalAnchor provides delegate.value,
    content = { content(state) },
  )
}

@Composable
@PublishedApi
internal inline fun <reified E, S> ContainedScope<E, S, *>.collectEffects(): State<SignalProvider>
  where E : AnchorScope<S, *> =
  anchorScope.signalManager
    .signals
    .map { SignalProvider { it } }
    .collectAsState(initial = SignalProvider { UnitSignal })

@Composable
@PublishedApi
internal inline fun <reified E, S> ContainedScope<E, S, *>.collectViewState(): State<S>
  where E : AnchorScope<S, *> =
  anchorScope.stateManager
    .states
    .collectAsState()
