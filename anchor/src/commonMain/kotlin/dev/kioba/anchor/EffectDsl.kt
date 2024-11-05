package dev.kioba.anchor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@AnchorDsl
public suspend inline fun <E, R> Anchor<E, *>.effect(
  coroutineContext: CoroutineContext = Dispatchers.IO,
  crossinline f: suspend E.() -> R,
): R where E : Effect =
  withContext(coroutineContext) {
    effects.f()
  }

@AnchorDsl
public suspend inline fun <E, S> Anchor<E, S>.anchor(
  f: Anchor<E, S>.() -> AnchorOf<Anchor<E, S>>,
) where E : Effect, S : ViewState {
  with(f()) {
    execute()
  }
}
