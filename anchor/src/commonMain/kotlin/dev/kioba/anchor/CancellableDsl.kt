package dev.kioba.anchor

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@AnchorDsl
public suspend inline fun <R, E, S> R.cancellable(
  singleRunKey: Any,
  crossinline f: suspend R.() -> Unit,
)
  where R : Anchor<E, S>, E : Effect, S : ViewState {
  val oldJob = jobs[singleRunKey]
  jobs[singleRunKey] =
    coroutineScope {
      launch {
        oldJob?.cancelAndJoin()
        f(this@cancellable)
      }
    }
}
