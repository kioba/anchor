package dev.kioba.anchor

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.compose.AnchorScopeDelegate
import dev.kioba.anchor.compose.LocalAnchor
import dev.kioba.anchor.dsl.Anchor


public inline fun <reified S, reified E> anchorScope(
  noinline initialState: () -> S,
  noinline effects: () -> E,
  init: () -> Anchor<AnchorScope<S, E>>,
): AnchorScope<S, E> =
  AnchorScope(
    initialState = initialState,
    effects = effects,
    init = init(),
  )

public inline fun <reified E> Modifier.execute(
  noinline block: () -> Anchor<E>,
): Modifier where E : AnchorDslScope =
  composed {
    val delegate = LocalAnchor.current
    Modifier.clickable { delegate.execute(block()) }
  }

@Composable
public inline fun <reified E> execute(
  noinline block: () -> Anchor<E>,
): () -> Unit
  where E : AnchorDslScope {
  val delegate: AnchorScopeDelegate = LocalAnchor.current
  return { delegate.execute(block()) }
}

