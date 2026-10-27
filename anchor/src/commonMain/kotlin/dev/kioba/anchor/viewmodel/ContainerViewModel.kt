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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Hosts an anchor for the lifetime of a ViewModel and starts it once:
 * `init` runs first, as a job of its own, then subscriptions start.
 *
 * Events emitted before subscriptions start, by `init` or by actions that
 * run meanwhile, are queued. Each `connect` handler that attaches as it
 * starts receives `Created`, by which time `init` has finished, then the
 * queued events in order, then later events. `SubscriptionAnchor.emit`
 * spells out which handlers attach as they start.
 *
 * Subscriptions start however `init` ends. A domain error or defect from
 * either step is routed to `onDomainError` or `defect` as before. A
 * cancellation from inside `init`, such as a `withTimeout` or a `raise()`
 * with no `onDomainError`, ends `init` silently. An unhandled failure is
 * reported like a failing action: it crashes on Android and iOS, and is
 * logged on desktop. Only clearing the ViewModel while `init` runs keeps
 * subscriptions from starting.
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
    launchAction {
      @Suppress("UNCHECKED_CAST")
      anchor.block()
    }
  }

  /**
   * Launches [block] on `viewModelScope` with `Dispatchers.Default`, inside the
   * anchor's error boundary, as a job of its own. A failure it does not handle
   * is reported like any uncaught coroutine failure and never cancels a
   * sibling job.
   */
  private fun launchAction(
    block: suspend () -> Unit,
  ): Job =
    viewModelScope.launch(Dispatchers.Default) {
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        block()
      }
    }

  init {
    viewModelScope.launch(Dispatchers.Default) {
      // init runs first, as a job of its own, the way execute() runs an action.
      // Whether it returns, has its error handled, is cancelled from inside
      // (withTimeout, a raise() with no onDomainError) or fails, it cannot
      // cancel this coroutine, so subscriptions start once it has ended. join()
      // throws only when viewModelScope itself is cancelled, and then no
      // handler starts.
      launchAction { anchor.consumeInitial() }.join()
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        with(anchor) {
          subscribe()
        }
      }
    }
  }
}
