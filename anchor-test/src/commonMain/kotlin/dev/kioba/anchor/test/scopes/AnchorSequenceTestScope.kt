package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.safeExecute
import dev.kioba.anchor.test.AnchorTestDsl

// ── Internal model ────────────────────────────────────────────────────────────

@PublishedApi
internal class SequenceStep<R, S, Err>(
  val given: GivenScopeImpl<R, S, Err>,
  val action: suspend Anchor<R, S, Err>.() -> Unit,
  val expectedActions: List<VerifyAction>,
  val domainErrorAssertion: Err?,
  val defectAssertion: Throwable?,
) where R : Effect, S : ViewState, Err : Any

// ── Public scopes ─────────────────────────────────────────────────────────────

@AnchorTestDsl
public class AnchorSequenceTestScope<R : Effect, S : ViewState, Err : Any>(
  @PublishedApi
  internal val anchorFactory: RememberAnchorScope.() -> Anchor<R, S, Err>,
) {
  @PublishedApi
  internal val outerGiven: GivenScopeImpl<R, S, Err> = GivenScopeImpl()

  @PublishedApi
  internal val steps: MutableList<SequenceStep<R, S, Err>> = mutableListOf()

  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<R, S, Err>.() -> Unit,
  ): Unit = outerGiven.block()

  public inline fun step(
    @Suppress("UNUSED_PARAMETER") description: String = "",
    block: AnchorStepScope<R, S, Err>.() -> Unit,
  ) {
    val stepScope = AnchorStepScope<R, S, Err>()
    stepScope.block()
    steps.add(
      checkNotNull(stepScope.builtStep) {
        "step() block must call verify() before it returns"
      },
    )
  }
}

@AnchorTestDsl
public class AnchorStepScope<R : Effect, S : ViewState, Err : Any> {
  @PublishedApi
  internal val stepGiven: GivenScopeImpl<R, S, Err> = GivenScopeImpl()

  @PublishedApi
  internal var pendingAction: (suspend Anchor<R, S, Err>.() -> Unit)? = null

  @PublishedApi
  internal var builtStep: SequenceStep<R, S, Err>? = null

  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: StepGivenScope<R, S, Err>.() -> Unit,
  ): Unit = stepGiven.block()

  public fun on(
    @Suppress("UNUSED_PARAMETER") description: String,
    anchorOf: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    check(pendingAction == null) { "on() called twice without an intervening verify()" }
    pendingAction = anchorOf
  }

  public inline fun verify(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: VerifyScope<R, S, Err>.() -> Unit,
  ) {
    val verifyScope = VerifyScopeImpl<R, S, Err>()
    verifyScope.block()
    val action = checkNotNull(pendingAction) { "verify() called without a preceding on()" }
    builtStep = SequenceStep(
      given = stepGiven,
      action = action,
      expectedActions = verifyScope.expectedActions.toList(),
      domainErrorAssertion = verifyScope.domainErrorAssertion,
      defectAssertion = verifyScope.defectAssertion,
    )
    pendingAction = null
  }
}

// ── Execution ─────────────────────────────────────────────────────────────────

@PublishedApi
internal suspend inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorSequenceTestScope<R, S, Err>.assertSequence() {
  val base: AnchorTestRuntime<R, S, Err> = buildBaseRuntime<R, S, Err>()
  var currentState: S = base.initState

  for (step in steps) {
    val stepEffectScope: R = base.effectScope
    val stepOnDomainError = step.given.onDomainError ?: base.onDomainError
    val stepDefect = step.given.defect ?: base.defect

    for (configure in step.given.effects) { stepEffectScope.configure() }

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
internal suspend inline fun <reified R : Effect, reified S : ViewState, Err : Any> AnchorSequenceTestScope<R, S, Err>.buildBaseRuntime(): AnchorTestRuntime<R, S, Err> {
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
          effectScope = outerGiven.effectScope as? R ?: effectScope(),
          initState = outerGiven.initState as? S ?: initialState(),
          onDomainError = outerGiven.onDomainError as? (suspend ErrorScope<R, S>.(Err) -> Unit) ?: onDomainError,
          defect = outerGiven.defect as? (suspend ErrorScope<R, S>.(Throwable) -> Unit) ?: defect,
        )
    }
  @Suppress("UNCHECKED_CAST")
  return rememberAnchorScope.anchorFactory() as AnchorTestRuntime<R, S, Err>
}
