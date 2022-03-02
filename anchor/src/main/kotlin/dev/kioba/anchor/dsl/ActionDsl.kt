package dev.kioba.anchor.dsl

import dev.kioba.anchor.MviDsl
import dev.kioba.anchor.MviScopeSyntax

public fun interface Action<E>
  where E : MviScopeSyntax {
  public suspend fun run(scope: E)
}

// TODO provide description meaning
@MviDsl
public fun <E> action(
  description: String,
  block: suspend E.() -> Unit,
): Action<E> where E : MviScopeSyntax =
  Action { scope -> scope.block() }

