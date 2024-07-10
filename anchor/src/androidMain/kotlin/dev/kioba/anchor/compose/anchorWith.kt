package dev.kioba.anchor.compose

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorChannel
import dev.kioba.anchor.AnchorDslScope

public inline fun <reified E> Modifier.anchorWith(
  noinline block: () -> Anchor<E>,
): Modifier where E : AnchorDslScope =
  this.then(composed {
    val delegate = LocalAnchor.current
    Modifier.clickable { delegate.execute(block()) }
  })

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
  noinline block: suspend E.() -> Unit,
): Modifier where E : AnchorDslScope =
  anchorWith { Anchor(block) }

@Composable
public inline fun <reified E> anchor(
  noinline block: E.() -> Unit,
): () -> Unit
  where E : AnchorDslScope =
  anchorWith { Anchor(block) }

@Composable
public inline fun <reified E> anchor(
  noinline block: suspend E.() -> Unit,
): () -> Unit
  where E : AnchorDslScope =
  anchorWith { Anchor(block) }
