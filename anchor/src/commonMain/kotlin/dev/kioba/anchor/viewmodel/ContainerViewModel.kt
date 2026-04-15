package dev.kioba.anchor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.RaisedException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

public class ContainerViewModel<R, S, Err>
  @PublishedApi
  internal constructor(
    internal val anchor: AnchorRuntime<R, S, Err>,
  ) : ViewModel(),
    AnchorScope<R, S>
  where
        R : Effect,
        S : ViewState,
        Err : Any {
  public val viewState: StateFlow<S>
    get() = anchor.viewState

  public val signals: Flow<SignalProvider>
    get() = anchor.signals

  override fun execute(
    block: suspend Anchor<R, S, *>.() -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      try {
        @Suppress("UNCHECKED_CAST")
        anchor.block()
      } catch (e: RaisedException) {
        @Suppress("UNCHECKED_CAST")
        val error = e.error as Err
        anchor.onDomainError?.invoke(anchor, error) ?: throw e
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        anchor.defect?.invoke(anchor, e) ?: throw e
      }
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
