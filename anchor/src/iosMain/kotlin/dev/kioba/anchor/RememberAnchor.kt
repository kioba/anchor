package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory

@Suppress("UNCHECKED_CAST")
public fun <S, E> rememberAnchor(
  scope: (RememberAnchorScope) -> Anchor<E, S>,
  customKey: String? = null,
): Anchor<E, S>
  where
  E : Effect,
  S : ViewState {
  val storeOwner = object : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
  }
  val factory = containerViewModelFactory { scope(AnchorRuntimeScope) as AnchorRuntime<E, S> }
  val provider = ViewModelProvider.create(storeOwner, factory)
  val anchorScope = when {
    customKey != null -> provider[customKey, ContainerViewModel::class]
    else -> provider[ContainerViewModel::class]
  } as ContainerViewModel<E, S>

//  anchorScope.coroutineScope.launch {
//    anchorScope.anchor._viewState
//      .collect { state -> states(state) }
//  }
//
//  anchorScope.coroutineScope.launch {
//    anchorScope.anchor.signals
//      .map { SignalProvider { it } }
//      .collect { signalProvider -> /*signals(signalProvider.provide())*/ }
//  }

  return anchorScope.anchor
}
