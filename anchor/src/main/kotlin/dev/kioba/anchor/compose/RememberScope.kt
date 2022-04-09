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
import dev.kioba.anchor.AnchorDslSyntax
import dev.kioba.anchor.UnitCommand
import dev.kioba.anchor.AnchorSyntax
import dev.kioba.anchor.dsl.Action
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Composable
public inline fun <reified E, S> RenderScope(
  noinline scope: @DisallowComposableCalls () -> E,
  crossinline content: @Composable (S) -> Unit,
) where E : AnchorSyntax,
        E : AnchorDslSyntax<S> {
  val environment = remember(scope) { scope() }

  val state by environment.collectViewState()
  val effect by environment.collectEffects()
  val delegate = environment.actionChannel()

  ConsumeInitial(environment, delegate)

  CompositionLocalProvider(
    LocalCommand provides effect,
    LocalScope provides delegate,
    content = { content(state) },
  )
}

@Composable
@PublishedApi
internal inline fun <reified E, S> ConsumeInitial(
  environment: E,
  delegate: ActionDelegate,
) where E : AnchorSyntax, E : AnchorDslSyntax<S> {
  LaunchedEffect(key1 = environment) {
    environment.environment.initialAction?.let { delegate.execute(it) }
  }
}

@Composable
@PublishedApi
internal inline fun <reified E, S> E.actionChannel(): ActionDelegate
  where E : AnchorSyntax,
        E : AnchorDslSyntax<S> {
  val coroutineScope = rememberCoroutineScope()
  return remember(this) {
    ActionDelegate { f ->
      coroutineScope.launch {
        convert<E>(f).run(this@actionChannel)
      }
    }
  }
}

@Composable
@PublishedApi
internal inline fun <reified E, S> E.collectEffects(): State<CommandProvider>
  where E : AnchorSyntax,
        E : AnchorDslSyntax<S> =
  environment.effectChannel
    .map { CommandProvider { it } }
    .collectAsState(initial = CommandProvider { UnitCommand })

@Composable
@PublishedApi
internal inline fun <reified E, S> E.collectViewState(): State<S>
  where E : AnchorSyntax,
        E : AnchorDslSyntax<S> =
  environment.stateChannel
    .collectAsState()

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <E> convert(
  action: Action<out AnchorSyntax>,
): Action<E>
  where E : AnchorSyntax =
  action as Action<E>
