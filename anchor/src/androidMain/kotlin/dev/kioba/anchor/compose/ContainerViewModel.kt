package dev.kioba.anchor.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.Action
import dev.kioba.anchor.ActionChannel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@PublishedApi
internal class ContainerViewModel<R, E, S>(
  override val anchor: R,
) : ViewModel(),
  ContainedScope<R, E, S> where
        R : Anchor<E, S>, E : Effect, S : ViewState {
  override val coroutineScope: CoroutineScope
    get() = viewModelScope

  override val actionChannel: ActionChannel =
    ActionChannel { f ->
      execute(f)
    }

  init {
    coroutineScope.launch {
      anchor.consumeInitial()
      with(anchor) {
        subscribe()
      }
    }
  }
}

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <R, E, S> convert(
  capture: Action<out Anchor<*, *>>,
): Action<R>
  where R : Anchor<E, S>, E : Effect, S : ViewState =
  capture as Action<R>
