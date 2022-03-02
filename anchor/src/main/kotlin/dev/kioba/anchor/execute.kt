package dev.kioba.anchor

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import dev.kioba.anchor.dsl.Action

public inline fun <reified E> Modifier.execute(
  noinline block: () -> Action<E>,
): Modifier where E : MviScopeSyntax =
  composed {
    val delegate = LocalScopeHolder.current
    Modifier.clickable { delegate.execute(block()) }
  }

@Composable
public inline fun <reified E> execute(
  noinline block: () -> Action<E>,
): () -> Unit
  where E : MviScopeSyntax {
  val delegate: ActionDelegate = LocalScopeHolder.current
  return { delegate.execute(block()) }
}

