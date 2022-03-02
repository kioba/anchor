package dev.kioba.anchor.dsl

import dev.kioba.anchor.MviDsl
import dev.kioba.anchor.MviScopeSyntax
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@MviDsl
public object LoopScope

@MviDsl
public suspend fun <E> E.execute(
  block: LoopScope.() -> Action<E>,
) where
  E : MviScopeSyntax {
  supervisorScope {
    launch { LoopScope.block().run(this@execute) }
  }
  }


