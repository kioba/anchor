package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

private class EapEffect(
  var data: String = "default",
  var calls: Int = 0,
) : Effect {
  suspend fun seed(v: String) {
    delay(10)
    data = v
  }

  fun fetch(): String {
    calls++
    return data
  }
}

private data class EapState(
  val result: String = "",
) : ViewState

private class EapFailure : RuntimeException()

private typealias EapAnchor = Anchor<EapEffect, EapState, Nothing>

private fun RememberAnchorScope.eapAnchor(): EapAnchor =
  create(
    initialState = ::EapState,
    effectScope = { EapEffect() },
  )

private suspend fun EapAnchor.fetchAndSet() {
  val d = effect { fetch() }
  reduce { copy(result = d) }
}

class EffectApisTest {

  // ── given { effect { } } ────────────────────────────────────────────────────

  /**
   * `given { effect { } }` runs before the action against the factory-built
   * effect scope, and may suspend (#53): the action reads the seeded value.
   */
  @Test
  fun givenEffectSeedsTheFactoryEffectScope() =
    runAnchorTest(RememberAnchorScope::eapAnchor) {
      given("a suspending seed on the factory's effect scope") {
        effect { seed("seeded") }
      }

      on("fetching and reducing", EapAnchor::fetchAndSet)

      verify("the action read the seeded data") {
        assertState { copy(result = "seeded") }
      }
    }

  /**
   * `given { effect { } }` blocks run against the resolved effect scope, so a
   * block declared before an `effectScope { }` override still applies to the
   * override.
   */
  @Test
  fun givenEffectAppliesToAnEffectScopeOverrideDeclaredAfterIt() =
    runAnchorTest(RememberAnchorScope::eapAnchor) {
      given("an effect block declared before an effect-scope override") {
        effect { data = "seeded" }
        effectScope { EapEffect(data = "override") }
      }

      on("fetching and reducing", EapAnchor::fetchAndSet)

      verify("the action read the seeded override") {
        assertState { copy(result = "seeded") }
      }
    }

  /**
   * The outer `given { effect { } }` of `runAnchorSequenceTest` runs against
   * the shared effect scope before the first step.
   */
  @Test
  fun outerGivenEffectSeedsTheSharedEffectScope() =
    runAnchorSequenceTest(RememberAnchorScope::eapAnchor) {
      given("an outer suspending seed") {
        effect { seed("outer") }
      }

      step("fetch") {
        on("fetching and reducing", EapAnchor::fetchAndSet)
        verify("step 1 read the outer seed") {
          assertState { copy(result = "outer") }
        }
      }
    }

  /**
   * The outer `given { effect { } }` is one-time setup: it runs once, not
   * before every step. Per-step changes belong in a step's own `given`.
   */
  @Test
  fun outerGivenEffectRunsOnceAcrossSteps() {
    var runs = 0
    runAnchorSequenceTest(RememberAnchorScope::eapAnchor) {
      given("an outer effect block that counts its runs") {
        effect { runs++ }
      }

      step("first") {
        on("reducing") { reduce { copy(result = "1") } }
        verify("first reduce") { assertState { copy(result = "1") } }
      }
      step("second") {
        on("reducing") { reduce { copy(result = "2") } }
        verify("second reduce") { assertState { copy(result = "2") } }
      }
    }
    assertEquals(1, runs)
  }

  /**
   * A throwing `given { effect { } }` is a broken test setup: it fails the test
   * with its own exception and never reaches the anchor's `defect` handler.
   */
  @Test
  fun throwingGivenEffectFailsTheTestWithoutReachingHandlers() {
    var defectSeen: Throwable? = null
    assertFailsWith<EapFailure> {
      runAnchorTest(RememberAnchorScope::eapAnchor) {
        given("a recording defect handler and a throwing effect block") {
          defect { defectSeen = it }
          effect { throw EapFailure() }
        }

        on("reducing") { reduce { copy(result = "ran") } }

        verify("the reduce") { assertState { copy(result = "ran") } }
      }
    }
    assertNull(defectSeen)
  }

  // ── assertEffect ────────────────────────────────────────────────────────────

  /**
   * `assertEffect` matches the recorded effect call without counting as a
   * recorded action, so it sits next to assertions that match every recorded
   * action. Its block runs with the effect scope after the action.
   */
  @Test
  fun assertEffectCoexistsWithExhaustiveAssertions() =
    runAnchorTest(RememberAnchorScope::eapAnchor) {
      given("the factory's effect scope") {}

      on("fetching and reducing", EapAnchor::fetchAndSet)

      verify("the effect call and the reduce") {
        assertEffect { assertEquals(1, calls) }
        assertState { copy(result = "default") }
      }
    }

  /**
   * `assertEffect` no longer stands in for a recorded action: a trailing
   * reduce left unasserted fails the count check.
   */
  @Test
  fun assertEffectDoesNotHideATrailingAction() {
    assertFailsWith<AssertionError> {
      runAnchorTest(RememberAnchorScope::eapAnchor) {
        given("the factory's effect scope") {}

        on("fetching and reducing twice") {
          fetchAndSet()
          reduce { copy(result = "unchecked") }
        }

        verify("the effect call and only the first reduce") {
          assertEffect { }
          assertState { copy(result = "default") }
        }
      }
    }
  }

  /**
   * A failing assertion inside the `assertEffect` block is the reported
   * failure, which proves the block runs.
   */
  @Test
  fun assertEffectBlockInspectsTheEffectScopeAfterTheAction() {
    val failure =
      assertFailsWith<AssertionError> {
        runAnchorTest(RememberAnchorScope::eapAnchor) {
          given("the factory's effect scope") {}

          on("fetching and reducing", EapAnchor::fetchAndSet)

          verify("an effect-scope inspection that does not hold") {
            assertEffect { assertEquals(99, calls, "effect-scope inspection") }
            assertState { copy(result = "default") }
          }
        }
      }
    assertContains(failure.message.orEmpty(), "effect-scope inspection")
  }

  /**
   * `assertEffect` fails, naming itself, when the action made no effect call.
   */
  @Test
  fun assertEffectFailsWhenNoEffectRan() {
    val failure =
      assertFailsWith<AssertionError> {
        runAnchorTest(RememberAnchorScope::eapAnchor) {
          given("the factory's effect scope") {}

          on("reducing without an effect call") { reduce { copy(result = "no effect") } }

          verify("an effect call and the reduce") {
            assertEffect { }
            assertState { copy(result = "no effect") }
          }
        }
      }
    assertContains(failure.message.orEmpty(), "assertEffect")
  }

  /**
   * `assertEffect` is positional: an effect call made before the reduce does
   * not match an `assertEffect` declared after that reduce's assertion.
   */
  @Test
  fun assertEffectFailsWhenDeclaredOutOfOrder() {
    val failure =
      assertFailsWith<AssertionError> {
        runAnchorTest(RememberAnchorScope::eapAnchor) {
          given("the factory's effect scope") {}

          on("fetching and reducing", EapAnchor::fetchAndSet)

          verify("the reduce, then the effect call") {
            assertState { copy(result = "default") }
            assertEffect { }
          }
        }
      }
    assertContains(failure.message.orEmpty(), "assertEffect")
  }

  /**
   * Unasserted effect calls are skipped: here the second of two leading
   * effect calls is never asserted, and the trailing one still matches.
   */
  @Test
  fun assertEffectSkipsEarlierUnassertedEffectCalls() =
    runAnchorTest(RememberAnchorScope::eapAnchor) {
      given("the factory's effect scope") {}

      on("two effect calls, a reduce, then another effect call") {
        effect { fetch() }
        val d = effect { fetch() }
        reduce { copy(result = d) }
        effect { fetch() }
      }

      verify("the first effect call, the reduce and the last effect call") {
        assertEffect { }
        assertState { copy(result = "default") }
        assertEffect { assertEquals(3, calls) }
      }
    }

  /**
   * An effect call is recorded when its block is entered, so an effect whose
   * block throws still counts as called.
   */
  @Test
  fun effectThatThrowsStillCountsAsCalled() =
    runAnchorTest(RememberAnchorScope::eapAnchor) {
      given("the factory's effect scope") {}

      on("an effect that throws, caught by the action") {
        try {
          effect<Unit> { throw EapFailure() }
        } catch (e: EapFailure) {
          reduce { copy(result = "caught") }
        }
      }

      verify("the effect call and the recovery reduce") {
        assertEffect { }
        assertState { copy(result = "caught") }
      }
    }

  /**
   * `assertEffect` matches against each step's own effect calls. The effect
   * scope is shared, so the second step's block sees both steps' calls.
   */
  @Test
  fun assertEffectWorksInsideASequenceStep() =
    runAnchorSequenceTest(RememberAnchorScope::eapAnchor) {
      given("the factory's effect scope") {}

      step("fetch without asserting the effect call") {
        on("fetching and reducing", EapAnchor::fetchAndSet)
        verify("the reduce") { assertState { copy(result = "default") } }
      }
      step("fetch and assert the effect call") {
        on("fetching and reducing", EapAnchor::fetchAndSet)
        verify("the effect call and the reduce") {
          assertEffect { assertEquals(2, calls) }
          assertState { copy(result = "default") }
        }
      }
    }
}
