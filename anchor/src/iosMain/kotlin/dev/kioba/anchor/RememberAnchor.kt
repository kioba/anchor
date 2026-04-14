package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory

/**
 * Creates and remembers an [Anchor] instance for iOS.
 *
 * This function provides a way to use Anchor in iOS targets, integrating with the lifecycle
 * of the component where it's called. It uses a [ViewModelStore] to retain the Anchor instance.
 *
 * @param S The [ViewState] type.
 * @param E The [Effect] type.
 * @param scope Factory function that creates the [Anchor] instance.
 * @param customKey Optional key for Anchor storage.
 * @return The [Anchor] instance.
 */
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

  return anchorScope.anchor
}

/**
 * Convenience extension to get a [NativeStateFlow] wrapper for the view state.
 *
 * Use this from iOS to collect state updates via callbacks.
 */
public fun <R : Effect, S : ViewState> AnchorSink<R, S>.nativeViewState(): NativeStateFlow<S> =
  NativeStateFlow(viewState)

/**
 * Convenience extension to get a [NativeSharedFlow] wrapper for signals.
 *
 * Use this from iOS to collect signal emissions via callbacks.
 */
public fun <R : Effect, S : ViewState> AnchorSink<R, S>.nativeSignals(): NativeSharedFlow<SignalProvider> =
  NativeSharedFlow(signals)
