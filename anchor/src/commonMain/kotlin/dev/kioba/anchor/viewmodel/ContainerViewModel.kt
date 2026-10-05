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
 * `init` runs first, then subscriptions start.
 *
 * Events emitted before subscriptions start, by `init` or by actions that
 * run meanwhile, are queued. Each `connect` handler that attaches as it
 * starts receives `Created`, by which time `init` has finished, then the
 * queued events in order, then later events. `SubscriptionAnchor.emit`
 * spells out which handlers attach as they start. A domain error or defect
 * from either step is routed to `onDomainError` or `defect` as before; once
 * handled, it does not skip the other step.
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
      // init runs first, so handlers start against the state it set; the
      // events it emits wait in the startup queue until subscribe() flushes
      // them. Each step has its own error boundary, so a handled error in
      // one never skips the other.
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
      }
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        with(anchor) {
          subscribe()
        }
      }
    }
  }
}
