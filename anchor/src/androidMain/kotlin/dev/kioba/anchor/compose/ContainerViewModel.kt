package dev.kioba.anchor.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.AnchorRuntime
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@PublishedApi
internal class ContainerViewModel<R, E, S>(
  override val anchor: R,
) : ViewModel(),
  ContainedScope<R, E, S> where
R : AnchorRuntime<E, S>, E : Effect, S : ViewState {

  override val coroutineScope: CoroutineScope = viewModelScope

  init {
    coroutineScope.launch(Dispatchers.Default) {
      anchor.consumeInitial()
      with(anchor) {
        subscribe()
      }
    }
  }
}
