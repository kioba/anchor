package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

@Composable
public fun <A> anchor(
  block: suspend A.() -> Unit,
): () -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val channel = LocalAnchor.currentTyped<A>()
  return { channel(block) }
}

@Composable
public fun <A, I> anchor(
  block: suspend A.(I) -> Unit,
): (I) -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val channel = LocalAnchor.currentTyped<A>()
  return { i -> channel { block(i) } }
}

@Composable
public fun <A, I, O> anchor(
  block: suspend A.(I, O) -> Unit,
): (I, O) -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val channel = LocalAnchor.currentTyped<A>()
  return { i, o -> channel { block(i, o) } }
}

@Composable
public fun <A, I, O, T> anchor(
  block: suspend A.(I, O, T) -> Unit,
): (I, O, T) -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val channel = LocalAnchor.currentTyped<A>()
  return { i, o, t -> channel { block(i, o, t) } }
}
