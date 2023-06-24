package dev.kioba.anchor

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.compose.AnchorChannel
import dev.kioba.anchor.compose.LocalAnchor
import dev.kioba.anchor.dsl.Anchor
import dev.kioba.anchor.dsl.SubscriptionsScope
import kotlinx.coroutines.launch


@Suppress("RedundantSamConstructor")
public inline fun <reified S, reified E> anchorScope(
  noinline initialState: () -> S,
  noinline effectScope: () -> E,
  crossinline init: suspend context(AnchorScope<S, E>) () -> Unit,
  noinline subscriptions: SubscriptionsScope<S, E>.() -> Unit = {},
): AnchorScope<S, E> =
  AnchorScope(
    initialState = initialState,
    effectScope = effectScope,
    init = Anchor<AnchorScope<S, E>> { scope -> launch { init(scope) } },
    subscriptions = subscriptions,
  )

public inline fun <reified E> Modifier.executeAnchor(
  noinline block: () -> Anchor<E>,
): Modifier where E : AnchorDslScope =
  composed {
    val delegate = LocalAnchor.current
    Modifier.clickable { delegate.execute(block()) }
  }

@Composable
public inline fun <reified E> executeAnchor(
  noinline block: () -> Anchor<E>,
): () -> Unit
  where E : AnchorDslScope {
  val delegate: AnchorChannel = LocalAnchor.current
  return { delegate.execute(block()) }
}


public inline fun <reified E> Modifier.anchor(
  noinline block: suspend context(E) () -> Unit,
): Modifier where E : AnchorDslScope =
  composed {
    val delegate = LocalAnchor.current
    Modifier.clickable { delegate.execute(Anchor<E> { block(this) }) }
  }


@Composable
public inline fun <reified E> anchor(
  noinline block: suspend context(E) () -> Unit,
): () -> Unit
  where E : AnchorDslScope {
  val delegate: AnchorChannel = LocalAnchor.current
  return { delegate.execute(Anchor<E> { block(this) }) }
}

@Composable
public inline fun <reified E> anchor(
  noinline block: context(E) () -> Unit,
): () -> Unit
  where E : AnchorDslScope {
  val delegate: AnchorChannel = LocalAnchor.current
  return { delegate.execute(Anchor<E> { block(this) }) }
}
