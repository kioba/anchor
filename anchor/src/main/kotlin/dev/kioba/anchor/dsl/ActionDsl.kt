package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorDslScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch

public fun interface Anchor<E>
  where E : AnchorDslScope {
  context(CoroutineScope)
    public fun run(scope: E)
}

@AnchorDsl
public fun <E> anchor(
  singleRunKey: Any? = null,
  block: suspend E.() -> Unit,
): Anchor<E>
  where E : AnchorDslScope =
  Anchor { scope ->
    when {
      singleRunKey != null -> {
        val oldJob = scope.cancellationManager.jobs[singleRunKey]
        scope.cancellationManager.jobs[singleRunKey] = launch {
          oldJob?.cancelAndJoin()
          scope.block()
        }
      }

      else -> launch { scope.block() }
    }
  }

