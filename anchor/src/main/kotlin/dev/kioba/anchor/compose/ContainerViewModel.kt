package dev.kioba.anchor.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.dsl.Anchor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

@PublishedApi
internal class ContainerViewModel<R, S, E>(
  override val anchorScope: R,
) : ViewModel(), ContainedScope<R, S, E> where
R : AnchorScope<S, E> {

  override val coroutineScope: CoroutineScope
    get() = viewModelScope

  override val actionChannel: AnchorChannel =
    AnchorChannel { f ->
      with(coroutineScope) {
        convert<R>(f).run(anchorScope)
      }
    }

  init {
    anchorScope.consumeInitial(actionChannel)
    listenSubscriptions()
  }

  private fun listenSubscriptions() {
    coroutineScope.launch {
      anchorScope.subscriptionManager
        .subscribe()
        .merge()
        .collect(actionChannel::execute)
    }
  }


}

@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <E> convert(
  anchor: Anchor<out AnchorDslScope>,
): Anchor<E>
  where E : AnchorDslScope =
  anchor as Anchor<E>

