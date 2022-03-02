package dev.kioba.anchor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.dsl.Action
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public fun interface ActionDelegate {
  public fun execute(action: Action<out MviScopeSyntax>)
}

@PublishedApi
internal fun interface EffectProvider {
   fun provide(): MviEffect
}

@PublishedApi
internal val LocalScopeHolder: ProvidableCompositionLocal<ActionDelegate> =
  staticCompositionLocalOf {
    error("Could not find an ActionDelegate provider")
  }

internal val LocalEffect: ProvidableCompositionLocal<EffectProvider> =
  staticCompositionLocalOf { EffectProvider { EmptyEffect } }

@Composable
internal inline fun <reified T> HandleEffect(
  noinline f: suspend (T) -> Unit,
) {
  val effectProvider = LocalEffect.current
  when (val effect = effectProvider.provide()) {
    is EmptyEffect -> Unit
    is T -> LaunchedEffect(effectProvider) { f(effect) }
  }
}

@Composable
internal inline fun <reified E, S> RenderScope(
  noinline scope: @DisallowComposableCalls () -> E,
  crossinline content: @Composable (S) -> Unit,
) where E : MviScopeSyntax,
        E : DslSyntax<S> {
  val environment = remember(scope) { scope() }

  val state by environment.collectViewState()
  val effect by environment.collectEffects()
  val delegate = environment.actionChannel()

  ConsumeInitial(environment, delegate)

  CompositionLocalProvider(
    LocalEffect provides effect,
    LocalScopeHolder provides delegate,
    content = { content(state) },
  )
}

@Composable
@PublishedApi
internal inline fun <reified E, S> ConsumeInitial(
  environment: E,
  delegate: ActionDelegate,
) where E : MviScopeSyntax, E : DslSyntax<S> {
  LaunchedEffect(key1 = environment) {
    environment.bridge.initialAction?.let { delegate.execute(it) }
  }
}

@Composable
@PublishedApi
internal inline fun <reified E, S> E.actionChannel(): ActionDelegate
  where E : MviScopeSyntax,
        E : DslSyntax<S> {
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
internal inline fun <reified E, S> E.collectEffects(): State<EffectProvider>
  where E : MviScopeSyntax,
        E : DslSyntax<S> =
  bridge.effectChannel
    .map { EffectProvider { it } }
    .collectAsState(initial = EffectProvider { EmptyEffect })

@Composable
@PublishedApi
internal inline fun <reified E, S> E.collectViewState(): State<S>
  where E : MviScopeSyntax,
        E : DslSyntax<S> =
  bridge.stateChannel
    .collectAsState()

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <E> convert(
  action: Action<out MviScopeSyntax>,
): Action<E>
  where E : MviScopeSyntax =
  action as Action<E>
