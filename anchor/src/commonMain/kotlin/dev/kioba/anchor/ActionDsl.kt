package dev.kioba.anchor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

public fun interface Anchor<E>
  where E : AnchorDslScope {
  public fun CoroutineScope.run(scope: E)
}

@AnchorDsl
public fun <E> Anchor(
  block: suspend E.() -> Unit,
): Anchor<E>
  where E : AnchorDslScope =
  Anchor { scope -> launch { scope.block() } }

@AnchorDsl
public suspend inline fun <E> E.cancellable(
  singleRunKey: Any,
  crossinline block: suspend E.() -> Unit,
)
  where E : AnchorDslScope {
  val oldJob = cancellationManager.jobs[singleRunKey]
  cancellationManager.jobs[singleRunKey] = coroutineScope {
    launch {
      oldJob?.cancelAndJoin()
      block(this@cancellable)
    }
  }
}
