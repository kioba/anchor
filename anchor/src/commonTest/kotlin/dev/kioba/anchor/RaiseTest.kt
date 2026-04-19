package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

sealed interface TestError {
  data object NotFound : TestError
  data class Invalid(val reason: String) : TestError
}

class RaiseTest {

  private fun createAnchor(
    onDomainError: (suspend Anchor<EmptyEffect, TestState, TestError>.(TestError) -> Unit)? = null,
    defect: (suspend Anchor<EmptyEffect, TestState, TestError>.(Throwable) -> Unit)? = null,
  ): AnchorRuntime<EmptyEffect, TestState, TestError> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      onDomainError = onDomainError,
      defect = defect,
    )

  @Test
  fun `raise throws RaisedException`() = runBlocking {
    val anchor = createAnchor()

    val exception = assertFailsWith<RaisedException> {
      anchor.raise(TestError.NotFound)
    }

    assertEquals(TestError.NotFound, exception.error)
  }

  @Test
  fun `orDie throws DomainDefectException`() = runBlocking {
    val anchor = createAnchor()

    val exception = assertFailsWith<DomainDefectException> {
      anchor.orDie(TestError.NotFound)
    }

    assertEquals(TestError.NotFound, exception.error)
  }

  @Test
  fun `raise carries typed error value`() = runBlocking {
    val anchor = createAnchor()

    val error = TestError.Invalid(reason = "bad input")
    val exception = assertFailsWith<RaisedException> {
      anchor.raise(error)
    }

    val captured = exception.error
    assertIs<TestError.Invalid>(captured)
    assertEquals("bad input", captured.reason)
  }

  @Test
  fun `raise inside cancellable propagates correctly`() = runBlocking {
    val anchor = createAnchor()

    val exception = assertFailsWith<RaisedException> {
      anchor.cancellable("test") {
        reduce { copy(value = 1) }
        raise(TestError.NotFound)
      }
    }

    assertEquals(TestError.NotFound, exception.error)
    // State should have been updated before raise
    assertEquals(1, anchor.state.value)
  }

  @Test
  fun `orDie inside cancellable propagates correctly`() = runBlocking {
    val anchor = createAnchor()

    val exception = assertFailsWith<DomainDefectException> {
      anchor.cancellable("test") {
        reduce { copy(value = 42) }
        orDie(TestError.Invalid("fatal"))
      }
    }

    assertIs<TestError.Invalid>(exception.error)
    assertEquals(42, anchor.state.value)
  }

  @Test
  fun `raise short-circuits execution`() = runBlocking {
    val anchor = createAnchor()
    var reachedAfterRaise = false

    assertFailsWith<RaisedException> {
      with(anchor) {
        reduce { copy(value = 1) }
        raise(TestError.NotFound)
        @Suppress("UNREACHABLE_CODE")
        reachedAfterRaise = true
      }
    }

    assertTrue(!reachedAfterRaise)
    assertEquals(1, anchor.state.value)
  }

  @Test
  fun `cancellable cleans up job after raise`() = runBlocking {
    val anchor = createAnchor()

    assertFailsWith<RaisedException> {
      anchor.cancellable("cleanup-test") {
        raise(TestError.NotFound)
      }
    }

    assertEquals(0, anchor.jobs.size, "Job should be cleaned up after raise")
  }

  @Test
  fun `raise inside subscription handler routes to onDomainError`() = runBlocking {
    val capturedErrors = mutableListOf<TestError>()
    val anchor = createAnchor()

    val scope = SubscriptionsScope<EmptyEffect, TestState, TestError>(
      chain = MutableSharedFlow(),
      anchor = anchor,
      effect = EmptyEffect,
      onDomainError = { error -> capturedErrors.add(error) },
    )

    val resultFlow = with(scope) {
      flowOf(Unit).anchor { raise(TestError.NotFound) }
    }

    resultFlow.collect {}

    assertEquals(listOf<TestError>(TestError.NotFound), capturedErrors)
  }

  @Test
  fun `ensure does nothing when condition is true`() = runBlocking {
    val anchor = createAnchor()

    with(anchor) {
      reduce { copy(value = 1) }
      ensure(true) { TestError.NotFound }
      reduce { copy(value = 2) }
    }

    assertEquals(2, anchor.state.value)
  }

  @Test
  fun `ensure raises when condition is false`() = runBlocking {
    val anchor = createAnchor()

    val exception = assertFailsWith<RaisedException> {
      anchor.ensure(false) { TestError.NotFound }
    }

    assertEquals(TestError.NotFound, exception.error)
  }

  @Test
  fun `ensure short-circuits execution`() = runBlocking {
    val anchor = createAnchor()
    var reachedAfterEnsure = false

    assertFailsWith<RaisedException> {
      with(anchor) {
        reduce { copy(value = 1) }
        ensure(false) { TestError.Invalid("check failed") }
        @Suppress("UNREACHABLE_CODE")
        reachedAfterEnsure = true
      }
    }

    assertTrue(!reachedAfterEnsure)
    assertEquals(1, anchor.state.value)
  }

  @Test
  fun `ensure inside cancellable propagates correctly`() = runBlocking {
    val anchor = createAnchor()

    val exception = assertFailsWith<RaisedException> {
      anchor.cancellable("ensure-test") {
        reduce { copy(value = 10) }
        ensure(false) { TestError.NotFound }
      }
    }

    assertEquals(TestError.NotFound, exception.error)
    assertEquals(10, anchor.state.value)
  }

  @Test
  fun `subscription pipeline survives raise when onDomainError is configured`() = runBlocking {
    val capturedErrors = mutableListOf<TestError>()
    val anchor = createAnchor()

    val scope = SubscriptionsScope<EmptyEffect, TestState, TestError>(
      chain = MutableSharedFlow(),
      anchor = anchor,
      effect = EmptyEffect,
      onDomainError = { error -> capturedErrors.add(error) },
    )

    val resultFlow = with(scope) {
      flowOf(1, 2, 3).anchor { raise(TestError.NotFound) }
    }

    resultFlow.collect {}

    assertEquals(3, capturedErrors.size, "All items should trigger onDomainError")
  }

  @Test
  fun `DomainDefectException in subscription anchor routes to defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor()

    val scope = SubscriptionsScope<EmptyEffect, TestState, TestError>(
      chain = MutableSharedFlow(),
      anchor = anchor,
      effect = EmptyEffect,
      onDomainError = { _ -> },
      defect = { error -> capturedDefects.add(error) },
    )

    val resultFlow = with(scope) {
      flowOf(Unit).anchor { orDie(TestError.NotFound) }
    }

    resultFlow.collect {}

    assertEquals(1, capturedDefects.size)
    assertIs<DomainDefectException>(capturedDefects.first())
  }

  @Test
  fun `general Throwable in subscription anchor routes to defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor()

    val scope = SubscriptionsScope<EmptyEffect, TestState, TestError>(
      chain = MutableSharedFlow(),
      anchor = anchor,
      effect = EmptyEffect,
      defect = { error -> capturedDefects.add(error) },
    )

    val resultFlow = with(scope) {
      flowOf(Unit).anchor { throw IllegalStateException("boom") }
    }

    resultFlow.collect {}

    assertEquals(1, capturedDefects.size)
    assertIs<IllegalStateException>(capturedDefects.first())
  }

  @Test
  fun `subscription pipeline survives defect when handler is configured`() = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor()

    val scope = SubscriptionsScope<EmptyEffect, TestState, TestError>(
      chain = MutableSharedFlow(),
      anchor = anchor,
      effect = EmptyEffect,
      defect = { error -> capturedDefects.add(error) },
    )

    val resultFlow = with(scope) {
      flowOf(1, 2, 3).anchor { throw RuntimeException("defect $it") }
    }

    resultFlow.collect {}

    assertEquals(3, capturedDefects.size, "All items should trigger defect handler")
  }

  @Test
  fun `subscription flow catch routes DomainDefectException to defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(
      defect = { error -> capturedDefects.add(error) },
    )

    // Error thrown outside .anchor() — caught by flow-level .catch{}
    val errorFlow = flow<Unit> { throw DomainDefectException(TestError.NotFound) }

    // Simulate what AnchorRuntime.handlers() does: per-flow .catch
    errorFlow
      .catch { e ->
        anchor.defect?.invoke(anchor, e) ?: throw e
      }
      .collect {}

    assertEquals(1, capturedDefects.size)
    assertIs<DomainDefectException>(capturedDefects.first())
  }

  @Test
  fun `subscription flow catch rethrows when no defect handler`(): Unit = runBlocking {
    val anchor = createAnchor(defect = null)

    val errorFlow = flow<Unit> { throw DomainDefectException(TestError.NotFound) }

    assertFailsWith<DomainDefectException> {
      errorFlow
        .catch { e ->
          anchor.defect?.invoke(anchor, e) ?: throw e
        }
        .collect {}
    }
  }
}
