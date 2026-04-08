package dev.kioba.anchor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public class ContainerViewModel<E, S> @PublishedApi internal constructor(
  internal val anchor: AnchorRuntime<E, S>,
) : ViewModel(),
  AnchorScope<E, S>
  where
        E : Effect,
        S : ViewState {

  public val viewState: StateFlow<S>
    get() = anchor.viewState

  public val signals: Flow<SignalProvider>
    get() = anchor.signals

  override fun execute(block: suspend Anchor<E, S>.() -> Unit) {
    viewModelScope.launch(Dispatchers.Default) {
      @Suppress("UNCHECKED_CAST")
      anchor.block()
    }
  }

  init {
    viewModelScope.launch(Dispatchers.Default) {
      anchor.consumeInitial()
      with(anchor) {
        subscribe()
      }
    }
  }
}
