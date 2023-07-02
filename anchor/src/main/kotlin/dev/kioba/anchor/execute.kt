package dev.kioba.anchor

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.compose.AnchorChannel
import dev.kioba.anchor.compose.LocalAnchor
import dev.kioba.anchor.dsl.Anchor
import dev.kioba.anchor.dsl.SubscriptionsScope
import kotlinx.coroutines.launch


public inline fun <reified S, reified E> anchorScope(
  noinline initialState: () -> S,
  noinline effectScope: () -> E,
  noinline init: (suspend context(AnchorScope<S, E>) () -> Unit)? = null,
  noinline subscriptions: SubscriptionsScope<S, E>.() -> Unit = {},
): AnchorScope<S, E> =
  AnchorScope(
    initialState = initialState,
    effectScope = effectScope,
    init = init?.let { Anchor<AnchorScope<S, E>> { scope -> launch { it(scope) } } },
    subscriptions = subscriptions,
  )

public inline fun <reified S> anchorScope(
  noinline initialState: () -> S,
  noinline init: (suspend context(AnchorScope<S, Unit>) () -> Unit)? = null,
  noinline subscriptions: SubscriptionsScope<S, Unit>.() -> Unit = {},
): AnchorScope<S, Unit> =
  anchorScope(
    initialState = initialState,
    effectScope = {},
    init = init,
    subscriptions = subscriptions,
  )

public inline fun <reified E> Modifier.anchorWith(
  noinline block: () -> Anchor<E>,
): Modifier where E : AnchorDslScope =
  composed {
    val delegate = LocalAnchor.current
    Modifier.clickable { delegate.execute(block()) }
  }

@Composable
public inline fun <reified E> anchorWith(
  noinline block: () -> Anchor<E>,
): () -> Unit
  where E : AnchorDslScope {
  val delegate: AnchorChannel = LocalAnchor.current
  return { delegate.execute(block()) }
}


@SuppressLint("UnnecessaryComposedModifier")
public inline fun <reified E> Modifier.anchor(
  noinline block: suspend context(E) () -> Unit,
): Modifier where E : AnchorDslScope =
  anchorWith { Anchor(block) }

@Composable
public inline fun <reified E> anchor(
  noinline block: context(E) () -> Unit,
): () -> Unit
  where E : AnchorDslScope =
  anchorWith { Anchor(block) }

@Composable
public inline fun <reified E> anchor(
  noinline block: suspend context(E) () -> Unit,
): () -> Unit
  where E : AnchorDslScope =
  anchorWith { Anchor(block) }

