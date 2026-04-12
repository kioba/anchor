package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@PublishedApi
internal interface ContainedScope<A, R, S>
  where
A : Anchor<R, S>,
R : Effect,
S : ViewState {
  val anchor: A
  val coroutineScope: CoroutineScope
}

@PublishedApi
internal fun <A, R, S> ContainedScope<A, R, S>.execute(
  block: suspend Anchor<*, *>.() -> Unit,
) where A : Anchor<R, S>, R : Effect, S : ViewState {
  coroutineScope.launch(Dispatchers.Default) { anchor.block() }
}
