package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorOf
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

public class AnchorTestScope<E : Effect, S : ViewState>(
  @PublishedApi
  internal val backgroundScope: CoroutineScope,
  @PublishedApi
  internal val testScheduler: TestCoroutineScheduler,
) {
  @PublishedApi
  internal val givenScope: GivenScopeImpl<E, S> = GivenScopeImpl()

  @PublishedApi
  internal val verifyScope: VerifyScopeImpl<E, S> = VerifyScopeImpl()

  @PublishedApi
  internal lateinit var action: () -> AnchorOf<Anchor<E, S>>

  @AnchorTestDsl
  public inline fun given(
    @Suppress("UNUSED_PARAMETER") description: String,
    block: GivenScope<E, S>.() -> Unit,
  ): Unit =
    givenScope.block()

  @AnchorTestDsl
  public fun on(
    @Suppress("UNUSED_PARAMETER") description: String,
    anchorOf: () -> AnchorOf<Anchor<E, S>>,
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

@OptIn(ExperimentalCoroutinesApi::class)
@PublishedApi
internal suspend inline fun <reified E : Effect, reified S : ViewState> AnchorTestScope<E, S>.assert() {
  val initialState = givenScope.assertInitialState()
  val effectScope = givenScope.assertEffectScope()
  val anchor =
    Anchor(
      initialState = { initialState },
      effectScope = { effectScope },
    )

  val actualActions = mutableListOf<VerifyAction>()
  backgroundScope.launch(context = UnconfinedTestDispatcher(testScheduler)) {
    merge(
      anchor.viewState.drop(1).map { ReducerAction<S> { it } },
      anchor.signals.map { SignalAction { it } },
      anchor.emitter.map { EventAction { it } },
    ).toList(actualActions)
  }

  with(action()) {
    anchor.execute()
  }

  assertEvents<E, S>(actualActions, initialState, effectScope)
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

@PublishedApi
internal inline fun <reified S> GivenScopeImpl<*, S>.assertInitialState(): S where S : ViewState =
  assertNotNull(
    actual = initState,
    message = initialStateMessage<S>(),
  )

@PublishedApi
internal inline fun <reified E> GivenScopeImpl<E, *>.assertEffectScope(): E where E : Effect =
  assertNotNull(
    actual = effectScope,
    message = effectScopeMessage<E>(),
  ).also { scope -> effects.forEach { f -> scope.f() } }

@PublishedApi
internal inline fun <reified S> initialStateMessage(): String where S : ViewState =
  """
  Initial State has not been provided!
  given {
    initialState { ${S::class.simpleName}() }
  }
  Initial State was null
  """.trimIndent()

@PublishedApi
internal inline fun <reified E> effectScopeMessage(): String where E : Effect =
  """
  Effect Scope has not been provided!
  given {
    effectScope { ${E::class.simpleName}() }
  }
  Effect Scope was null
  """.trimIndent()
