package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorDslScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

public fun interface Anchor<E>
  where E : AnchorDslScope {
  context(CoroutineScope)
    public fun run(scope: E)
}

@AnchorDsl
public fun <E> Anchor(
  block: suspend E.() -> Unit,
): Anchor<E>
  where E : AnchorDslScope =
  Anchor { scope -> launch { scope.block() } }

context(E)
  @AnchorDsl
  public suspend inline fun <E> cancellable(
  singleRunKey: Any,
  crossinline block: suspend E.() -> Unit,
)
  where E : AnchorDslScope {
  val oldJob = cancellationManager.jobs[singleRunKey]
  cancellationManager.jobs[singleRunKey] = coroutineScope {
    launch {
      oldJob?.cancelAndJoin()
      block(this@E)
    }
  }
}

