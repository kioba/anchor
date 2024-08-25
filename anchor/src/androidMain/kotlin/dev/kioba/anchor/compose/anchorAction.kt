package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import dev.kioba.anchor.Action
import dev.kioba.anchor.ActionChannel
import dev.kioba.anchor.Anchor

@Composable
public inline fun <reified E> anchor(
  noinline block: () -> Action<E>,
): () -> Unit
  where E : Anchor<*, *> {
  val channel: ActionChannel = LocalAnchor.current
  return { channel.execute(block()) }
}
