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

  // Non-null only while executing a step {} block; gives {} inside step routes here.
  @PublishedApi
  internal var currentStepGiven: GivenScopeImpl<R, S, Err>? = null

  // Always-present accumulator — on {} and verify {} always land here.
  @PublishedApi
  internal val stepBuilder: SequenceStepBuilder<R, S, Err> = SequenceStepBuilder()

  // True only while executing a sequence {} block — used solely to validate step() placement.
  @PublishedApi
  internal var insideSequence: Boolean = false

  // ── DSL ──────────────────────────────────────────────────────────────────────

  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<R, S, Err>.() -> Unit,
  ): Unit =
    (currentStepGiven ?: givenScope).block()

  public fun on(
    @Suppress("UNUSED_PARAMETER") description: String,
    anchorOf: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    check(stepBuilder.pendingAction == null) { "on() called twice without an intervening verify()" }
    stepBuilder.pendingAction = anchorOf
  }

  public inline fun verify(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: VerifyScope<R, S, Err>.() -> Unit,
  ) {
    val stepVerify = VerifyScopeImpl<R, S, Err>()
    stepVerify.block()
    val action =
      checkNotNull(stepBuilder.pendingAction) {
        "verify() called without a preceding on()"
      }
    stepBuilder.steps.add(
      SequenceStep(
        given = currentStepGiven ?: GivenScopeImpl(),
        action = action,
        expectedActions = stepVerify.expectedActions.toList(),
        domainErrorAssertion = stepVerify.domainErrorAssertion,
        defectAssertion = stepVerify.defectAssertion,
      ),
    )
    currentStepGiven = null
    stepBuilder.pendingAction = null
  }

  public suspend inline fun sequence(
    @Suppress("UNUSED_PARAMETER") description: String,
    crossinline block: suspend AnchorTestScope<R, S, Err>.() -> Unit,
  ) {
    insideSequence = true
    block()
    insideSequence = false
  }

  public inline fun step(
    @Suppress("UNUSED_PARAMETER") description: String = "",
    block: AnchorTestScope<R, S, Err>.() -> Unit,
  ) {
    check(insideSequence) { "step() must be called inside sequence {}" }
    currentStepGiven = GivenScopeImpl()
    block()
    // verify() resets currentStepGiven when it finalises the step
  }
}

// ── Internal helpers ─────────────────────────────────────────────────────────

@PublishedApi
internal class SequenceStepBuilder<R : Effect, S : ViewState, Err : Any> {
  @PublishedApi
  internal var pendingAction: (suspend Anchor<R, S, Err>.() -> Unit)? = null

  @PublishedApi
  internal val steps: MutableList<SequenceStep<R, S, Err>> = mutableListOf()
}

@PublishedApi
internal class SequenceStep<R, S, Err>(
  val given: GivenScopeImpl<R, S, Err>,
  val action: suspend Anchor<R, S, Err>.() -> Unit,
  val expectedActions: List<VerifyAction>,
  val domainErrorAssertion: Err?,
  val defectAssertion: Throwable?,
) where R : Effect, S : ViewState, Err : Any

// ── Execution ────────────────────────────────────────────────────────────────

// A single test is a sequence of exactly one step — always use assertSequence.
@PublishedApi
internal suspend inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assert() {
  assertSequence<R, S, Err>()
}

@PublishedApi
internal suspend inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.buildBaseRuntime(): AnchorTestRuntime<R, S, Err> {
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
        AnchorTestRuntime(
          effectScope = givenScope.effectScope as? R ?: effectScope(),
          initState = givenScope.initState as? S ?: initialState(),
          onDomainError = givenScope.onDomainError as? (suspend ErrorScope<R, S>.(Err) -> Unit) ?: onDomainError,
          defect = givenScope.defect as? (suspend ErrorScope<R, S>.(Throwable) -> Unit) ?: defect,
        )
    }
  @Suppress("UNCHECKED_CAST")
  return rememberAnchorScope.anchorFactory() as AnchorTestRuntime<R, S, Err>
}

@PublishedApi
internal suspend inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assertSequence() {
  val base: AnchorTestRuntime<R, S, Err> = buildBaseRuntime<R, S, Err>()
  var currentState: S = base.initState

  for (step in stepBuilder.steps) {
    val stepEffectScope: R = step.given.effectScope ?: base.effectScope
    val stepOnDomainError = step.given.onDomainError ?: base.onDomainError
    val stepDefect = step.given.defect ?: base.defect

    val stepRuntime =
      AnchorTestRuntime<R, S, Err>(
        effectScope = stepEffectScope,
        initState = currentState,
        onDomainError = stepOnDomainError,
        defect = stepDefect,
      )

    val recordingDomainError: suspend ErrorScope<R, S>.(Err) -> Unit = { error: Err ->
      stepRuntime.capturedDomainError = error
      stepOnDomainError?.invoke(this, error)
    }

    val recordingDefect: suspend ErrorScope<R, S>.(Throwable) -> Unit = { throwable: Throwable ->
      stepRuntime.capturedDefect = throwable
      stepDefect?.invoke(this, throwable)
    }

    safeExecute(stepRuntime, recordingDomainError, recordingDefect) {
      step.action.invoke(stepRuntime)
    }

    assertEvents<R, S, Err>(
      actualActions = stepRuntime.verifyActions,
      initialState = currentState,
      effectScope = stepEffectScope,
      expectedActions = step.expectedActions,
    )

    assertHandlers(
      anchor = stepRuntime,
      domainErrorAssertion = step.domainErrorAssertion,
      defectAssertion = step.defectAssertion,
    )

    currentState = stepRuntime.state
  }
}

@PublishedApi
internal fun <R : Effect, S : ViewState, Err : Any> AnchorTestScope<R, S, Err>.assertHandlers(
  anchor: AnchorTestRuntime<R, S, Err>,
  domainErrorAssertion: Err?,
  defectAssertion: Throwable?,
) {
  if (domainErrorAssertion != null) {
    assertEquals(domainErrorAssertion, anchor.capturedDomainError, "Expected domain error does not match")
  } else {
    assertNull(anchor.capturedDomainError, "Expected no domain error, but got: ${anchor.capturedDomainError}")
  }

  if (defectAssertion != null) {
    val actual = assertNotNull(anchor.capturedDefect, "Expected defect handler to be called, but it was not")
    if (defectAssertion is DomainDefectException && actual is DomainDefectException) {
      assertEquals(defectAssertion.error, actual.error, "Expected defect error does not match")
    } else {
      assertEquals(defectAssertion, actual, "Expected defect does not match")
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
  expectedActions: List<VerifyAction>,
) {
  assertEquals(expectedActions.size, actualActions.size)

  expectedActions
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
