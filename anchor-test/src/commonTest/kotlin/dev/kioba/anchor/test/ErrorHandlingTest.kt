package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.getOrElse
import dev.kioba.anchor.recover
import kotlin.test.Test

private sealed interface TestErr {
  data object NotFound : TestErr
}

private class TestEffect : Effect

private data class TestViewState(
  val value: Int = 0,
) : ViewState

private typealias TestAnchor = Anchor<TestEffect, TestViewState, TestErr>

private fun RememberAnchorScope.testAnchor(): TestAnchor =
  create(
    initialState = ::TestViewState,
    effectScope = { TestEffect() },
  )

// -- Helpers --

private suspend fun TestAnchor.raiseWithRecover() {
  recover { raise(TestErr.NotFound) }
    .getOrElse { reduce { copy(value = 42) } }
}

private suspend fun TestAnchor.raiseAndReraise() {
  recover { raise(TestErr.NotFound) }.getOrRaise()
}

private suspend fun TestAnchor.orDieInsideRecover() {
  recover { orDie(TestErr.NotFound) }
    .getOrElse { reduce { copy(value = 42) } }
}

// -- Tests --

class ErrorHandlingTest {

  @Test
  fun raiseInsideRecoverRoutesToGetOrElse() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("a default state") {}

      on("raising inside recover") { raiseWithRecover() }

      verify("raise was caught and getOrElse fallback ran") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = 42) }
      }
    }

  @Test
  fun raiseAndReraisePropagatesToBddFramework() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("a default state") {}

      on("raising and reraising") { raiseAndReraise() }

      verify("both raises were recorded") {
        assertRaise { TestErr.NotFound }
        assertRaise { TestErr.NotFound }
      }
    }

  @Test
  fun orDieInsideRecoverEscalates() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("a default state") {}

      on("calling orDie inside recover") { orDieInsideRecover() }

      verify("orDie was recorded") {
        assertOrDie { TestErr.NotFound }
      }
    }

}
