package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import dev.kioba.anchor.ActionChannel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorOf
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

//@Composable
//public inline fun <reified E> anchor(
//  noinline block: () -> AnchorOf<E>,
//): () -> Unit
//  where E : Anchor<*, *> {
//  val channel: ActionChannel = LocalAnchor.current
//  return { channel.execute(block()) }
//}

@Composable
public fun <A> anchor(
  block: suspend A.() -> Unit,
): () -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val channel: ActionChannel = LocalAnchor.current
  val anchorOf = AnchorOf(block)
  return { channel.execute(anchorOf) }
}
