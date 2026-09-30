package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.Event
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalScope
import dev.kioba.anchor.SubscriptionScope
import dev.kioba.anchor.ViewState
import kotlin.coroutines.CoroutineContext

/**
 * Recording [Anchor] that [dev.kioba.anchor.test.runAnchorTest] and
 * [dev.kioba.anchor.test.runAnchorSequenceTest] build in place of the production runtime.
 *
 * It trades production semantics for deterministic verification: every operation runs inline in
 * the caller's coroutine, and `reduce`, `post`, `emit`, `raise` and `orDie` are appended to
 * [verifyActions] for `verify` to match in order. It deliberately differs from the production
 * runtime:
 *
 * - [cancellable] runs its block inline and keeps no jobs, so it never cancels a block with the
 *   same key that is still running.
 * - [effect] runs its block in the caller's context and ignores the requested [CoroutineContext],
 *   dispatcher included. Effect calls are recorded apart from [verifyActions], in
 *   [effectCallPositions], for `assertEffect` only.
 * - [post] and [emit] only record. There is no signal stream and no event bus, so nothing reaches
 *   a signal collector or a `connect()` handler.
 * - The factory's `init` and `subscriptions` blocks never run: the `create` overrides in the
 *   `buildBaseRuntime` functions of [AnchorTestScope] and [AnchorSequenceTestScope] drop them.
 *
 * Behavior that depends on any of these is only exercised by the production runtime.
 */
@PublishedApi
internal class AnchorTestRuntime<R, S, Err>(
  @PublishedApi
  internal val effectScope: R,
  @PublishedApi
  internal val initState: S,
  @PublishedApi
  internal val onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)? = null,
  @PublishedApi
  internal val defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)? = null,
) : Anchor<R, S, Err>() where R : Effect, S : ViewState, Err : Any {

  val verifyActions = mutableListOf<VerifyAction>()

  /** `verifyActions.size` at each `effect { }` entry, in call order. Read by `assertEffect`. */
  @PublishedApi
  internal val effectCallPositions: MutableList<Int> = mutableListOf()

  @PublishedApi
  internal var capturedDomainError: Err? = null

  @PublishedApi
  internal var capturedDefect: Throwable? = null
  private var currentState = initState

  override val state: S
    get() = currentState

  /**
   * Records the signal and returns. Nothing is delivered: there is no signal stream or collector,
   * so the production runtime's delivery rules (for example, what happens to a signal posted
   * before an accepting collector attaches) are not modeled.
   */
  override suspend fun post(
    block: SignalScope.() -> Signal
  ) {
    verifyActions.add(SignalAction { block(SignalScope) })
  }

  /**
   * Records the event and returns. There is no event bus and no `connect()` handler runs, so
   * subscription chains the event would trigger are not exercised.
   */
  override suspend fun emit(
    block: SubscriptionScope.() -> Event,
  ) {
    verifyActions.add(EventAction { block(SubscriptionScope) })
  }

  /**
   * Runs [block] inline and returns when it finishes. Unlike the production runtime it keeps no
   * job per [key], so it does NOT cancel a block with the same key that is still running: both
   * run to completion and both record their actions.
   */
  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    block()
  }

  /**
   * Runs [block] against the effect scope in the caller's context and returns its result.
   * [coroutineContext] is ignored, so the block does NOT switch to the requested dispatcher.
   * It records the call's position in [effectCallPositions] before running [block], so a block
   * that throws still counts as called.
   */
  override suspend fun <T> effect(
    coroutineContext: CoroutineContext,
    block: suspend R.() -> T,
  ): T {
    effectCallPositions.add(verifyActions.size)
    return block(effectScope)
  }

  override fun reduce(
    reducer: S.() -> S
  ) {
    verifyActions.add(ReducerAction(reducer))
    currentState = currentState.reducer()
  }

  override fun raise(error: Err): Nothing {
    verifyActions.add(RaiseAction(error))
    throw RaisedException(error)
  }

  override fun orDie(error: Err): Nothing {
    verifyActions.add(OrDieAction(error))
    throw DomainDefectException(error)
  }
}
