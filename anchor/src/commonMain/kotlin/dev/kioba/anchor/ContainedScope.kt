package dev.kioba.anchor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@PublishedApi
internal interface ContainedScope<R, E, S> where R : Anchor<E, S>, E : Effect, S : ViewState {
  val anchor: R
  val coroutineScope: CoroutineScope
}

@PublishedApi
internal fun <R, E, S> ContainedScope<R, E, S>.execute(
  block: suspend Anchor<*, *>.() -> Unit,
) where R : Anchor<E, S>, E : Effect, S : ViewState {
  coroutineScope.launch(Dispatchers.Default) { anchor.block() }
}
