package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.fold
import dev.kioba.anchor.getErrorOrNull
import dev.kioba.anchor.getOrElse
import dev.kioba.anchor.getOrNull
import dev.kioba.anchor.orDie
import dev.kioba.anchor.recover
import kotlin.test.Test

private sealed interface TestErr {
  data object NotFound : TestErr
  data class InvalidInput(val reason: String) : TestErr
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

// -- Helpers: recover + getOrElse --

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

// -- Helpers: raise propagation --

private suspend fun TestAnchor.raiseDirectly() {
  raise(TestErr.NotFound)
}

// -- Helpers: recover ok path --

private suspend fun TestAnchor.recoverOkPath() {
  val result = recover { 42 }.getOrElse { -1 }
  reduce { copy(value = result) }
}

// -- Helpers: getOrNull --

private suspend fun TestAnchor.recoverGetOrNullOk() {
  val result = recover { 42 }.getOrNull()
  reduce { copy(value = result ?: -1) }
}

private suspend fun TestAnchor.recoverGetOrNullError() {
  val result = recover { raise(TestErr.NotFound) }.getOrNull()
  reduce { copy(value = result ?: -1) }
}

// -- Helpers: getErrorOrNull --

private suspend fun TestAnchor.recoverGetErrorOrNullOk() {
  val err = recover { 42 }.getErrorOrNull()
  reduce { copy(value = if (err == null) 1 else 0) }
}

private suspend fun TestAnchor.recoverGetErrorOrNullError() {
  val err = recover { raise(TestErr.NotFound) }.getErrorOrNull()
  reduce { copy(value = if (err != null) 1 else 0) }
}

// -- Helpers: fold --

private suspend fun TestAnchor.recoverFoldOk() {
  val result = recover { 42 }.fold(onError = { -1 }, onOk = { it * 2 })
  reduce { copy(value = result) }
}

private suspend fun TestAnchor.recoverFoldError() {
  val result = recover { raise(TestErr.NotFound) }.fold(onError = { -1 }, onOk = { it })
  reduce { copy(value = result) }
}

// -- Helpers: ensure --

private suspend fun TestAnchor.ensureTrue() {
  ensure(true) { TestErr.NotFound }
  reduce { copy(value = 1) }
}

private suspend fun TestAnchor.ensureFalseWithRecover() {
  recover { ensure(false) { TestErr.NotFound } }
    .getOrElse { reduce { copy(value = -1) } }
}

// -- Helpers: orDie on Recover --

private suspend fun TestAnchor.orDieOnRecoverOk() {
  val r = recover { 42 }
  val v = orDie(r)
  reduce { copy(value = v) }
}

private suspend fun TestAnchor.orDieOnRecoverError() {
  val r = recover { raise(TestErr.NotFound) }
  orDie(r)
}

// -- Helpers: multiple recovers --

private suspend fun TestAnchor.multipleRecovers() {
  recover { raise(TestErr.NotFound) }
    .getOrElse { reduce { copy(value = 1) } }
  recover { raise(TestErr.InvalidInput("bad")) }
    .getOrElse { reduce { copy(value = 2) } }
}

// -- Helpers: different error variant --

private suspend fun TestAnchor.raiseInvalidInput() {
  recover { raise(TestErr.InvalidInput("bad input")) }
    .getOrRaise()
}

// -- Tests --

class ErrorHandlingTest {

  // ===== Existing tests =====

  /**
   * Verifies that `raise()` inside an Anchor-level `recover` block is caught
   * by the recover and the `getOrElse` fallback executes. The raise is still
   * recorded in verifyActions, and the fallback reduce is captured too.
   */
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

  /**
   * Verifies that `raise` followed by `getOrRaise()` produces two raise
   * records: the initial raise inside recover, and the re-raise from
   * getOrRaise. Since the second raise escapes recover, it propagates
   * to the domain error handler.
   */
  @Test
  fun raiseAndReraisePropagatesToBddFramework() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("a default state") {}

      on("raising and reraising") { raiseAndReraise() }

      verify("both raises were recorded") {
        assertRaise { TestErr.NotFound }
        assertRaise { TestErr.NotFound }
        assertDomainError { TestErr.NotFound }
      }
    }

  /**
   * Verifies that `orDie()` inside a recover block is NOT caught by recover
   * (it throws DomainDefectException, not RaisedException). The orDie is
   * recorded, and the defect handler is invoked.
   */
  @Test
  fun orDieInsideRecoverEscalates() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("a default state") {}

      on("calling orDie inside recover") { orDieInsideRecover() }

      verify("orDie was recorded") {
        assertOrDie { TestErr.NotFound }
        assertDefect { DomainDefectException(TestErr.NotFound) }
      }
    }

  // ===== New: raise propagation =====

  /**
   * Verifies that `raise()` without a recover block propagates to the
   * `onDomainError` handler registered via given. The raise is recorded
   * in verifyActions, and the domain error handler is invoked with
   * the error value.
   */
  @Test
  fun raiseDirectlyPropagatesToDomainHandler() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("a domain error handler") {
        onDomainError { reduce { copy(value = -1) } }
      }

      on("raising directly") { raiseDirectly() }

      verify("raise recorded and handler invoked") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = -1) }
        assertDomainError { TestErr.NotFound }
      }
    }

  // ===== New: recover Ok path =====

  /**
   * Verifies that `recover { value }` when no raise occurs returns
   * `Recover.Ok(value)`. The `getOrElse` fallback is NOT called, and
   * the success value is used in a reduce.
   */
  @Test
  fun recoverOkReturnsValue() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover succeeds") { recoverOkPath() }

      verify("value 42 from recover Ok used in reduce") {
        assertState { copy(value = 42) }
      }
    }

  // ===== New: getOrNull =====

  /**
   * Verifies that `getOrNull()` on a successful `Recover.Ok` returns the
   * value (not null). The non-null value is used in a reduce.
   */
  @Test
  fun getOrNullOnOkReturnsValue() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover ok then getOrNull") { recoverGetOrNullOk() }

      verify("value 42 from getOrNull") {
        assertState { copy(value = 42) }
      }
    }

  /**
   * Verifies that `getOrNull()` on a `Recover.Error` returns null.
   * The null fallback produces value = -1 in the reduce.
   */
  @Test
  fun getOrNullOnErrorReturnsNull() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover error then getOrNull") { recoverGetOrNullError() }

      verify("raise recorded, null fallback produced -1") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = -1) }
      }
    }

  // ===== New: getErrorOrNull =====

  /**
   * Verifies that `getErrorOrNull()` on a successful `Recover.Ok` returns
   * null. The null check produces value = 1 (error was null).
   */
  @Test
  fun getErrorOrNullOnOkReturnsNull() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover ok then getErrorOrNull") { recoverGetErrorOrNullOk() }

      verify("no error, value = 1") {
        assertState { copy(value = 1) }
      }
    }

  /**
   * Verifies that `getErrorOrNull()` on a `Recover.Error` returns the error.
   * The non-null check produces value = 1 (error was present).
   */
  @Test
  fun getErrorOrNullOnErrorReturnsError() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover error then getErrorOrNull") { recoverGetErrorOrNullError() }

      verify("raise recorded, error present, value = 1") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = 1) }
      }
    }

  // ===== New: fold =====

  /**
   * Verifies that `fold` on a successful `Recover.Ok` invokes the `onOk`
   * branch. The value 42 is doubled to 84.
   */
  @Test
  fun foldOnOkCallsOnOk() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover ok then fold") { recoverFoldOk() }

      verify("onOk doubled the value to 84") {
        assertState { copy(value = 84) }
      }
    }

  /**
   * Verifies that `fold` on a `Recover.Error` invokes the `onError` branch.
   * The fallback produces -1.
   */
  @Test
  fun foldOnErrorCallsOnError() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("recover error then fold") { recoverFoldError() }

      verify("raise recorded, onError produced -1") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = -1) }
      }
    }

  // ===== New: ensure =====

  /**
   * Verifies that `ensure(true) { error }` does NOT raise — execution
   * continues to the subsequent reduce. No raise is recorded.
   */
  @Test
  fun ensureTrueDoesNotRaise() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("ensure with true condition") { ensureTrue() }

      verify("no raise, reduce ran") {
        assertState { copy(value = 1) }
      }
    }

  /**
   * Verifies that `ensure(false) { error }` inside a recover block raises
   * the error, which is caught by recover. The `getOrElse` fallback runs
   * and produces value = -1.
   */
  @Test
  fun ensureFalseInsideRecoverCatchesRaise() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("ensure false inside recover") { ensureFalseWithRecover() }

      verify("raise recorded and fallback ran") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = -1) }
      }
    }

  // ===== New: orDie on Recover =====

  /**
   * Verifies that `orDie(recover { value })` on a successful Recover
   * returns the value without escalating. No defect is triggered.
   */
  @Test
  fun orDieOnRecoverOkReturnsValue() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("orDie on successful recover") { orDieOnRecoverOk() }

      verify("value 42 used in reduce, no defect") {
        assertState { copy(value = 42) }
      }
    }

  /**
   * Verifies that `orDie(recover { raise(error) })` escalates the error
   * to a defect. The initial raise inside recover is recorded, then
   * orDie escalates, and the defect handler is invoked.
   */
  @Test
  fun orDieOnRecoverErrorEscalates() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("orDie on failed recover") { orDieOnRecoverError() }

      verify("raise and orDie recorded, defect handler invoked") {
        assertRaise { TestErr.NotFound }
        assertOrDie { TestErr.NotFound }
        assertDefect { DomainDefectException(TestErr.NotFound) }
      }
    }

  // ===== New: multiple recovers =====

  /**
   * Verifies that multiple sequential recover-getOrElse blocks each
   * record their raises independently. Both fallback reduces are
   * captured in order.
   */
  @Test
  fun multipleRecoverGetOrElseBlocks() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("two sequential recover blocks") { multipleRecovers() }

      verify("both raises and both fallback reduces recorded") {
        assertRaise { TestErr.NotFound }
        assertState { copy(value = 1) }
        assertRaise { TestErr.InvalidInput("bad") }
        assertState { copy(value = 2) }
      }
    }

  // ===== New: error variants =====

  /**
   * Verifies that different sealed interface variants of the error type
   * are correctly recorded and distinguishable. Uses InvalidInput with
   * a data payload instead of the singleton NotFound.
   */
  @Test
  fun raiseWithDifferentErrorVariants() =
    runAnchorTest(RememberAnchorScope::testAnchor) {
      given("default state") {}

      on("raising InvalidInput") { raiseInvalidInput() }

      verify("both raise records show InvalidInput") {
        assertRaise { TestErr.InvalidInput("bad input") }
        assertRaise { TestErr.InvalidInput("bad input") }
        assertDomainError { TestErr.InvalidInput("bad input") }
      }
    }
}
