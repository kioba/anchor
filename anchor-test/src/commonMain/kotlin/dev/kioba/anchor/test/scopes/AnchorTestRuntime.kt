package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalScope
import dev.kioba.anchor.SubscriptionScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.RaisedException
import kotlin.coroutines.CoroutineContext

@PublishedApi
internal class AnchorTestRuntime<R, S, Err>(
  @PublishedApi
  internal val effectScope: R,
  @PublishedApi
  internal val initState: S,
) : Anchor<R, S, Err>() where R : Effect, S : ViewState, Err : Any {

  val verifyActions = mutableListOf<VerifyAction>()
  private var currentState = initState

  override val state: S
    get() = currentState

  override suspend fun post(
    block: SignalScope.() -> Signal
  ) {
    verifyActions.add(SignalAction { block(SignalScope) })
  }

  override suspend fun emit(
    block: SubscriptionScope.() -> Event,
  ) {
    verifyActions.add(EventAction { block(SubscriptionScope) })
  }

  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    block()
  }

  override suspend fun <T> effect(
    coroutineContext: CoroutineContext,
    block: suspend R.() -> T,
  ): T =
    block(effectScope)

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
