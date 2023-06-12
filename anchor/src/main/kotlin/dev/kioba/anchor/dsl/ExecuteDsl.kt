package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorDslScope
import kotlinx.coroutines.supervisorScope

@AnchorDsl
public object ExecuteScope

@AnchorDsl
public suspend fun <E> E.anchorWith(
  block: ExecuteScope.() -> Anchor<E>,
) where
  E : AnchorDslScope {
  supervisorScope {
    ExecuteScope.block()
      .run(this@anchorWith)
  }
}
