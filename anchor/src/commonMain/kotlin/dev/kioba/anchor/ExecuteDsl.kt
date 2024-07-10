package dev.kioba.anchor

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
  }
}
