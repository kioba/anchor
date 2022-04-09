package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorSyntax

public fun interface Action<E>
  where E : AnchorSyntax {
  public suspend fun run(scope: E)
}

@AnchorDsl
public fun <E> action(
  block: suspend E.() -> Unit,
): Action<E> where E : AnchorSyntax =
  Action { scope -> scope.block() }

