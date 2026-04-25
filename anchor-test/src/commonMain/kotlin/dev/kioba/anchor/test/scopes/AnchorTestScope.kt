package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

  @AnchorTestDsl
  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<R, S, Err>.() -> Unit,
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
        onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)?,
        defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
        subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)?,
      ): Anchor<R, S, Err> =
        AnchorTestRuntime<R, S, Err>(
          effectScope = givenScope.effectScope as? R ?: effectScope(),
          initState = givenScope.initState as? S ?: initialState(),
          onDomainError = onDomainError,
          defect = defect,
        )
    }

  val anchor: AnchorTestRuntime<R, S, Err> = rememberAnchorScope.anchorFactory() as AnchorTestRuntime<R, S, Err>

  try {
    anchor.action()
  } catch (e: RaisedException) {
    val resolvedOnDomainError = givenScope.onDomainError ?: anchor.onDomainError
    if (resolvedOnDomainError != null) {
      @Suppress("UNCHECKED_CAST")
      val error = e.error as Err
      anchor.domainErrors.add(error)
      resolvedOnDomainError.invoke(anchor, error)
    }
  } catch (e: CancellationException) {
    throw e
  } catch (e: DomainDefectException) {
    val resolvedDefect = givenScope.defect ?: anchor.defect
    if (resolvedDefect != null) {
      anchor.defects.add(e)
      resolvedDefect.invoke(anchor, e)
    }
  }

  assertEvents<R, S, Err>(anchor.verifyActions, anchor.initState, anchor.effectScope)
  assertHandlers(anchor)
}

@PublishedApi
internal fun <R : Effect, S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assertHandlers(
  anchor: AnchorTestRuntime<R, S, Err>,
) {
  verifyScope.domainErrorAssertion?.let { assertion ->
    assertTrue(anchor.domainErrors.isNotEmpty(), "Expected onDomainError to be called, but it was not")
    assertEquals(assertion(), anchor.domainErrors.first())
  }

  if (verifyScope.noDomainErrorFlag) {
    assertTrue(anchor.domainErrors.isEmpty(), "Expected no domain error, but got: ${anchor.domainErrors}")
  }

  verifyScope.defectAssertion?.let { assertion ->
    assertTrue(anchor.defects.isNotEmpty(), "Expected defect handler to be called, but it was not")
    assertEquals(assertion(), anchor.defects.first())
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
