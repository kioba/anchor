package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl
import kotlin.test.assertEquals
import kotlin.test.assertIs

public class AnchorTestScope<E : Effect, S : ViewState>(
  @PublishedApi
  internal val anchorFactory: RememberAnchorScope.() -> Anchor<E, S>,
) {
  @PublishedApi
  internal val givenScope: GivenScopeImpl<E, S> = GivenScopeImpl()

  @PublishedApi
  internal val verifyScope: VerifyScopeImpl<E, S> = VerifyScopeImpl()

  @PublishedApi
  internal lateinit var action: suspend Anchor<E, S>.() -> Unit

  @AnchorTestDsl
  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<E, S>.() -> Unit,
  ): Unit =
    givenScope.block()

  @AnchorTestDsl
  public fun on(
    @Suppress("UNUSED_PARAMETER") description: String,
    anchorOf: suspend Anchor<E, S>.() -> Unit,
  ) {
    action = anchorOf
  }

  @AnchorTestDsl
  public inline fun verify(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: VerifyScope<E, S>.() -> Unit,
  ) {
    verifyScope.block()
  }
}

@PublishedApi
internal suspend inline fun <reified E : Effect, reified S : ViewState> AnchorTestScope<E, S>.assert() {
  val rememberAnchorScope = object : RememberAnchorScope {
    @Suppress("UNCHECKED_CAST")
    override fun <E : Effect, S : ViewState> create(
      effectScope: () -> E,
      initialState: () -> S,
      init: (suspend Anchor<E, S>.() -> Unit)?,
      subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)?
    ): Anchor<E, S> =
      AnchorTestRuntime(
        givenScope.effectScope as? E ?: effectScope(),
        givenScope.initState as? S ?: initialState(),
      )
  }
  val anchor: AnchorTestRuntime<E, S> = rememberAnchorScope.anchorFactory() as AnchorTestRuntime<E, S>

  anchor.action()

  assertEvents<E, S>(anchor.verifyActions, anchor.initState, anchor.effectScope)
}

@PublishedApi
internal inline fun <reified E : Effect, reified S : ViewState> AnchorTestScope<E, S>.assertEvents(
  actualActions: MutableList<VerifyAction>,
  initialState: S,
  effectScope: E,
) {
  assertEquals(verifyScope.expectedActions.size, actualActions.size)

  verifyScope.expectedActions
    .runningFold(initialState) { currentState, action ->
      when (action) {
        is EffectAction<*> -> {
          @Suppress("UNCHECKED_CAST")
          (action as EffectAction<E>).effect(effectScope)
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
      }
    }
}
