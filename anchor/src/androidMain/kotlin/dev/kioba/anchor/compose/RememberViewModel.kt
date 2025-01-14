package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisallowComposableCalls
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.AnchorRuntime
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

@Suppress("UNCHECKED_CAST")
@PublishedApi
@Composable
internal inline fun <reified R, E, S> ViewModelStoreOwner.rememberViewModel(
  key: String,
  noinline factory: @DisallowComposableCalls () -> R,
): ContainedScope<R, E, S> where R : AnchorRuntime<E, S>, E : Effect, S : ViewState {
  val scopeFactory = rememberUpdatedState(newValue = factory)

  return remember(key1 = key) {
    ViewModelProvider(
      this,
      ContainerViewModelFactory { ContainerViewModel(scopeFactory.value()) },
    )[key, ContainerViewModel::class.java] as ContainedScope<R, E, S>
  }
}
