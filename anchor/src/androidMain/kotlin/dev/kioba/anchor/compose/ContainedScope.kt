package dev.kioba.anchor.compose

import dev.kioba.anchor.Action
import dev.kioba.anchor.ActionChannel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@PublishedApi
internal interface ContainedScope<R, E, S> where R : Anchor<E, S>, E : Effect, S : ViewState {
  val anchor: R
  val coroutineScope: CoroutineScope
  val actionChannel: ActionChannel
}

internal fun <R, E, S> ContainedScope<R, E, S>.execute(
  f: Action<out Anchor<*, *>>,
) where R : Anchor<E, S>, E : Effect, S : ViewState {
  coroutineScope
    .launch {
      with(convert<R, E, S>(f)) {
        anchor.execute()
      }
    }
}
