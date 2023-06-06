package dev.kioba.anchor

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.compose.AnchorScopeDelegate
import dev.kioba.anchor.compose.LocalAnchor
import dev.kioba.anchor.dsl.AnchorEffect


public inline fun <reified S> anchorScope(
  noinline initialState: () -> S,
  init: AnchorEffect<AnchorScope<S>>,
): AnchorScope<S> =
  AnchorScope(
    initialState = initialState,
    init = init,
  )


public inline fun <reified E> Modifier.execute(
  noinline block: () -> AnchorEffect<E>,
): Modifier where E : AnchorDslScope =
  composed {
    val delegate = LocalAnchor.current
    Modifier.clickable { delegate.execute(block()) }
  }

@Composable
public inline fun <reified E> execute(
  noinline block: () -> AnchorEffect<E>,
): () -> Unit
  where E : AnchorDslScope {
  val delegate: AnchorScopeDelegate = LocalAnchor.current
  return { delegate.execute(block()) }
}

