package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.dsl.Anchor
import dev.kioba.anchor.dsl.UnitSignal
import kotlinx.coroutines.flow.map

@Composable
public inline fun <reified C, S> RememberAnchorScope(
  noinline scope: @DisallowComposableCalls () -> C,
  crossinline content: @Composable (S) -> Unit,
) where C : AnchorScope<S, *> {
  val anchorScope = remember(scope) { scope() }

  val state by anchorScope.collectViewState()
  val signal by anchorScope.collectEffects()
  val delegate = anchorScope.actionChannel()
    .apply { consumeInitial(anchorScope) }

  CompositionLocalProvider(
    LocalSignals provides signal,
    LocalAnchor provides delegate,
    content = { content(state) },
  )
}

@Composable
@PublishedApi
internal inline fun <reified E, S> AnchorScopeDelegate.consumeInitial(
  scope: E,
) where E : AnchorScope<S, *> {
  LaunchedEffect(key1 = scope) {
    execute(scope.initManager.init)
  }
}

@Composable
@PublishedApi
internal inline fun <reified E, S> E.actionChannel(): AnchorScopeDelegate
  where E : AnchorScope<S, *> {
  val coroutineScope = rememberCoroutineScope()
  return remember(this) {
    AnchorScopeDelegate { f ->
      with(coroutineScope) {
        convert<E>(f).run(this@actionChannel)
      }
    }
  }
}

@Composable
@PublishedApi
internal inline fun <reified E, S> E.collectEffects(): State<SignalProvider>
  where E : AnchorScope<S, *> =
  signalManager.signals
    .map { SignalProvider { it } }
    .collectAsState(initial = SignalProvider { UnitSignal })

@Composable
@PublishedApi
internal inline fun <reified E, S> E.collectViewState(): State<S>
  where E : AnchorScope<S, *> =
  stateManager.states
    .collectAsState()

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <E> convert(
  anchor: Anchor<out AnchorDslScope>,
): Anchor<E>
  where E : AnchorDslScope =
  anchor as Anchor<E>
