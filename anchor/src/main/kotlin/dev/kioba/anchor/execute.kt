package dev.kioba.anchor

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.compose.ActionDelegate
import dev.kioba.anchor.compose.LocalScope
import dev.kioba.anchor.dsl.Action

public inline fun <reified E> Modifier.execute(
  noinline block: () -> Action<E>,
): Modifier where E : AnchorSyntax =
  composed {
    val delegate = LocalScope.current
    Modifier.clickable { delegate.execute(block()) }
  }

@Composable
public inline fun <reified E> execute(
  noinline block: () -> Action<E>,
): () -> Unit
  where E : AnchorSyntax {
  val delegate: ActionDelegate = LocalScope.current
  return { delegate.execute(block()) }
}

