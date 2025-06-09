package dev.kioba.anchor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.AnchorRuntime
import dev.kioba.anchor.ContainedScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@PublishedApi
internal class ContainerViewModel<E, S>(
  override val anchor: AnchorRuntime<E, S>,
) : ViewModel(),
  ContainedScope<AnchorRuntime<E, S>, E, S>
  where
E : Effect,
S : ViewState {

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
