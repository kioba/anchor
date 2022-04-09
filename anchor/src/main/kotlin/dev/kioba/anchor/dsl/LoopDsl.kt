package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorSyntax
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@AnchorDsl
public object ExecuteScope

@AnchorDsl
public suspend fun <E> E.execute(
  block: ExecuteScope.() -> Action<E>,
) where
  E : AnchorSyntax {
  supervisorScope {
    launch { ExecuteScope.block().run(this@execute) }
  }
  }


