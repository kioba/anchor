package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.test.AnchorTestDsl
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertIs

public class AnchorTestScope<R : Effect, S : ViewState, Err : Any>(
  @PublishedApi
  internal val anchorFactory: RememberAnchorScope.() -> Anchor<R, S, Err>,
) {
  @PublishedApi
  internal val givenScope: GivenScopeImpl<R, S> = GivenScopeImpl()

  @PublishedApi
  internal val verifyScope: VerifyScopeImpl<R, S, Err> = VerifyScopeImpl()

  @PublishedApi
  internal lateinit var action: suspend Anchor<R, S, Err>.() -> Unit

  @AnchorTestDsl
  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<R, S>.() -> Unit,
  ): Unit =
    givenScope.block()

  @AnchorTestDsl
  public fun on(
    @Suppress("UNUSED_PARAMETER") description: String,
    anchorOf: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    action = anchorOf
  }

  @AnchorTestDsl
  public inline fun verify(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: VerifyScope<R, S, Err>.() -> Unit,
  ) {
    verifyScope.block()
  }
}

@PublishedApi
internal suspend inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assert() {
  val rememberAnchorScope =
    object : RememberAnchorScope {
      @Suppress("UNCHECKED_CAST")
      override fun <R : Effect, S : ViewState, Err : Any> create(
        effectScope: () -> R,
        initialState: () -> S,
        init: (suspend Anchor<R, S, Err>.() -> Unit)?,
        onDomainError: (suspend Anchor<R, S, Err>.(Err) -> Unit)?,
        defect: (suspend Anchor<R, S, Err>.(Throwable) -> Unit)?,
        subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)?,
      ): Anchor<R, S, Err> =
        AnchorTestRuntime<R, S, Err>(
          givenScope.effectScope as? R ?: effectScope(),
          givenScope.initState as? S ?: initialState(),
        )
    }
  val anchor: AnchorTestRuntime<R, S, Err> = rememberAnchorScope.anchorFactory() as AnchorTestRuntime<R, S, Err>

  try {
    anchor.action()
  } catch (_: RaisedException) {
    // Expected when action calls raise() — recorded in verifyActions
  } catch (e: CancellationException) {
    throw e
  } catch (_: DomainDefectException) {
    // Expected when action calls orDie() — recorded in verifyActions
  }

  assertEvents<R, S, Err>(anchor.verifyActions, anchor.initState, anchor.effectScope)
}

@PublishedApi
internal inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assertEvents(
  actualActions: MutableList<VerifyAction>,
  initialState: S,
  effectScope: R,
) {
  assertEquals(verifyScope.expectedActions.size, actualActions.size)

  verifyScope.expectedActions
    .runningFold(initialState) { currentState, action ->
      when (action) {
        is EffectAction<*> -> {
          @Suppress("UNCHECKED_CAST")
          (action as EffectAction<R>).effect(effectScope)
          currentState
        }

        is EventAction -> {
          val actualEvent = assertIs<EventAction>(actualActions.removeFirstOrNull())
          assertEquals(action.event(), actualEvent.event())
          currentState
        }

        is ReducerAction<*> -> {
          val actualReducer = assertIs<ReducerAction<S>>(actualActions.removeFirstOrNull())
          val newState = actualReducer.reduce(currentState)
          val newStateDouble = actualReducer.reduce(currentState)
          assertEquals(newState, newStateDouble)
          @Suppress("UNCHECKED_CAST")
          assertEquals((action as ReducerAction<S>).reduce(currentState), newState)
          newState
        }

        is SignalAction -> {
          val actualSignal = assertIs<SignalAction>(actualActions.removeFirstOrNull())
          assertEquals(action.signal(), actualSignal.signal())
          currentState
        }

        is RaiseAction -> {
          val actualRaise = assertIs<RaiseAction>(actualActions.removeFirstOrNull())
          assertEquals(action.error, actualRaise.error)
          currentState
        }

        is OrDieAction -> {
          val actualOrDie = assertIs<OrDieAction>(actualActions.removeFirstOrNull())
          assertEquals(action.error, actualOrDie.error)
          currentState
        }
      }
    }
}
