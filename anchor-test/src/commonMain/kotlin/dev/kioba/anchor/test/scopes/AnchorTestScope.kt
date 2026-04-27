package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.safeExecute
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

public class AnchorTestScope<R : Effect, S : ViewState, Err : Any>(
  @PublishedApi
  internal val anchorFactory: RememberAnchorScope.() -> Anchor<R, S, Err>,
) {
  @PublishedApi
  internal val givenScope: GivenScopeImpl<R, S, Err> = GivenScopeImpl()

  @PublishedApi
  internal val verifyScope: VerifyScopeImpl<R, S, Err> = VerifyScopeImpl()

  @PublishedApi
  internal lateinit var action: suspend Anchor<R, S, Err>.() -> Unit

  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<R, S, Err>.() -> Unit,
  ): Unit =
    givenScope.block()

  public fun on(
    @Suppress("UNUSED_PARAMETER") description: String,
    anchorOf: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    action = anchorOf
  }

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
        onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)?,
        defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
        subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)?,
      ): Anchor<R, S, Err> =
        AnchorTestRuntime<R, S, Err>(
          effectScope = givenScope.effectScope as? R ?: effectScope(),
          initState = givenScope.initState as? S ?: initialState(),
          onDomainError = givenScope.onDomainError as? (suspend ErrorScope<R, S>.(Err) -> Unit) ?: onDomainError,
          defect = givenScope.defect as? (suspend ErrorScope<R, S>.(Throwable) -> Unit) ?: defect,
        )
    }

  val anchor: AnchorTestRuntime<R, S, Err> = rememberAnchorScope.anchorFactory() as AnchorTestRuntime<R, S, Err>

  val recordingOnDomainError: suspend ErrorScope<R, S>.(Err) -> Unit = { error: Err ->
    anchor.capturedDomainError = error
    anchor.onDomainError?.invoke(this, error)
  }

  val recordingDefect: suspend ErrorScope<R, S>.(Throwable) -> Unit = { throwable: Throwable ->
    anchor.capturedDefect = throwable
    anchor.defect?.invoke(this, throwable)
  }

  safeExecute(anchor, recordingOnDomainError, recordingDefect) {
    anchor.action()
  }

  assertEvents<R, S, Err>(anchor.verifyActions, anchor.initState, anchor.effectScope)
  assertHandlers(anchor)
}

@PublishedApi
internal fun <R : Effect, S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assertHandlers(
  anchor: AnchorTestRuntime<R, S, Err>,
) {
  val expectedDomainError = verifyScope.domainErrorAssertion
  if (expectedDomainError != null) {
    assertEquals(expectedDomainError, anchor.capturedDomainError, "Expected domain error does not match")
  } else {
    assertNull(anchor.capturedDomainError, "Expected no domain error, but got: ${anchor.capturedDomainError}")
  }

  val expectedDefect = verifyScope.defectAssertion
  if (expectedDefect != null) {
    val actual = assertNotNull(anchor.capturedDefect, "Expected defect handler to be called, but it was not")
    if (expectedDefect is DomainDefectException && actual is DomainDefectException) {
      assertEquals(expectedDefect.error, actual.error, "Expected defect error does not match")
    } else {
      assertEquals(expectedDefect, actual, "Expected defect does not match")
    }
  } else {
    assertNull(anchor.capturedDefect, "Expected no defect, but got: ${anchor.capturedDefect}")
  }
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

        is RaiseAction<*> -> {
          val actualRaise = assertIs<RaiseAction<*>>(actualActions.removeFirstOrNull())
          assertEquals(action.error, actualRaise.error)
          currentState
        }

        is OrDieAction<*> -> {
          val actualOrDie = assertIs<OrDieAction<*>>(actualActions.removeFirstOrNull())
          assertEquals(action.error, actualOrDie.error)
          currentState
        }
      }
    }
}
