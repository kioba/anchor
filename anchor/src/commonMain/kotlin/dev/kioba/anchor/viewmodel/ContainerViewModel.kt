package dev.kioba.anchor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.safeExecute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Hosts an anchor for the lifetime of a ViewModel and starts it once:
 * subscriptions attach first, then `init` runs.
 *
 * Each `connect` handler therefore receives `Created` (on attach) before any
 * event emitted by `init`, and events `init` emits reach every live handler.
 * A domain error or defect from either step is routed to `onDomainError` or
 * `defect` as before; once handled, it does not skip the other step.
 */
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
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        @Suppress("UNCHECKED_CAST")
        anchor.block()
      }
    }
  }

  init {
    viewModelScope.launch(Dispatchers.Default) {
      // Subscriptions attach before init runs, so events init emits reach
      // them. Each step has its own error boundary, so a handled error in
      // one never skips the other.
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        with(anchor) {
          subscribe()
        }
      }
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
      }
    }
  }
}
