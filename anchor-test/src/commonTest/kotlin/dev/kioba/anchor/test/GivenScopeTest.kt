package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlin.test.Test

private data class GivenEffect(
  val data: String = "default",
) : Effect

private data class GivenViewState(
  val value: Int = 0,
  val label: String = "",
) : ViewState

// -- PureAnchor fixtures (no error type) --

private typealias PureGivenAnchor = Anchor<GivenEffect, GivenViewState, Nothing>

private fun RememberAnchorScope.pureGivenAnchor(): PureGivenAnchor =
  create(
    initialState = ::GivenViewState,
    effectScope = { GivenEffect() },
  )

// -- Error-capable fixtures --

private sealed interface GivenErr {
  data object NotFound : GivenErr
}

private typealias ErrGivenAnchor = Anchor<GivenEffect, GivenViewState, GivenErr>

private fun RememberAnchorScope.errGivenAnchor(): ErrGivenAnchor =
  create(
    initialState = ::GivenViewState,
    effectScope = { GivenEffect() },
  )

class GivenScopeTest {

  /**
   * Verifies that `initialState` in the given block overrides the factory
   * default. The action reduces from the overridden state (value = 99),
   * proving the custom initial state was applied.
   */
  @Test
  fun initialStateOverridesFactoryDefault() =
    runAnchorTest(RememberAnchorScope::pureGivenAnchor) {
      given("custom initial state") {
        initialState { GivenViewState(value = 99) }
      }

      on("reducing from overridden state") {
        reduce { copy(value = value + 1) }
      }

      verify("state started from 99") {
        assertState { copy(value = value + 1) }
      }
    }

  /**
   * Verifies that `effectScope` in the given block replaces the factory's
   * default effect scope. The action calls `effect { data }` which reads
   * from the custom scope providing "custom" instead of "default".
   */
  @Test
  fun effectScopeReplacesDefault() =
    runAnchorTest(RememberAnchorScope::pureGivenAnchor) {
      given("custom effect scope") {
        effectScope { GivenEffect(data = "custom") }
      }

      on("reading from effect scope") {
        val d = effect { data }
        reduce { copy(label = d) }
      }

      verify("effect scope had custom data") {
        assertState { copy(label = "custom") }
      }
    }

  /**
   * Verifies that when no effectScope override is provided, the factory's
   * default effect scope is used. The default GivenEffect has data = "default".
   */
  @Test
  fun effectScopeDefaultFromFactory() =
    runAnchorTest(RememberAnchorScope::pureGivenAnchor) {
      given("no effect scope override") {}

      on("reading from default effect scope") {
        val d = effect { data }
        reduce { copy(label = d) }
      }

      verify("effect scope had factory default data") {
        assertState { copy(label = "default") }
      }
    }

  /**
   * Verifies that `onDomainError` in the given block overrides the factory's
   * error handler. The custom handler reduces state with a marker value,
   * proving it was invoked instead of the factory default (which is null).
   */
  @Test
  fun onDomainErrorOverridesFactory() =
    runAnchorTest(RememberAnchorScope::errGivenAnchor) {
      given("custom domain error handler") {
        onDomainError { reduce { copy(value = -1) } }
      }

      on("raising a domain error") { raise(GivenErr.NotFound) }

      verify("custom handler ran and reduced state") {
        assertRaise { GivenErr.NotFound }
        assertState { copy(value = -1) }
        assertDomainError { GivenErr.NotFound }
      }
    }

  /**
   * Verifies that `defect` in the given block overrides the factory's
   * defect handler. The custom handler reduces state with the throwable
   * message, proving it was invoked.
   */
  @Test
  fun defectOverridesFactory() =
    runAnchorTest(RememberAnchorScope::errGivenAnchor) {
      given("custom defect handler") {
        defect { t -> reduce { copy(label = t.message.orEmpty()) } }
      }

      on("calling orDie") { orDie(GivenErr.NotFound) }

      verify("custom defect handler ran") {
        assertOrDie { GivenErr.NotFound }
        assertState { copy(label = "Domain defect: ${GivenErr.NotFound}") }
        assertDefect { DomainDefectException(GivenErr.NotFound) }
      }
    }

  /**
   * Verifies that `initialState` and `effectScope` can both be overridden
   * in the same given block and compose correctly. The action reads from
   * the custom effect scope and reduces from the custom initial state.
   */
  @Test
  fun initialStateAndEffectScopeCompose() =
    runAnchorTest(RememberAnchorScope::pureGivenAnchor) {
      given("both overridden") {
        initialState { GivenViewState(value = 10, label = "") }
        effectScope { GivenEffect(data = "composed") }
      }

      on("using both overrides") {
        val d = effect { data }
        reduce { copy(value = value + 1, label = d) }
      }

      verify("state started from 10 and effect had composed data") {
        assertState { copy(value = value + 1, label = "composed") }
      }
    }
}
