package dev.kioba.anchor.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.ActionChannel
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorOf
import dev.kioba.anchor.AnchorRuntime
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@PublishedApi
internal class ContainerViewModel<R, E, S>(
  override val anchor: R,
) : ViewModel(),
  ContainedScope<R, E, S> where
R : AnchorRuntime<E, S>, E : Effect, S : ViewState {
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
  capture: AnchorOf<out Anchor<*, *>>,
): AnchorOf<R>
  where R : Anchor<E, S>, E : Effect, S : ViewState =
  capture as AnchorOf<R>
