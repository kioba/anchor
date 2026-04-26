package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@PublishedApi
internal interface ContainedScope<A, R, S, Err>
  where
A : Anchor<R, S, Err>,
R : Effect,
S : ViewState,
Err : Any {
  val anchor: A
  val coroutineScope: CoroutineScope
  val onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)?
  val defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?
}

@PublishedApi
internal fun <A, R, S, Err> ContainedScope<A, R, S, Err>.execute(
  block: suspend Anchor<*, *, *>.() -> Unit,
) where A : Anchor<R, S, Err>, R : Effect, S : ViewState, Err : Any {
  coroutineScope.launch(Dispatchers.Default) {
    safeExecute(anchor, onDomainError, defect) {
      anchor.block()
    }
  }
}
