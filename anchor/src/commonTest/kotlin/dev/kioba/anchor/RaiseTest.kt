package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.flow.MutableSharedFlow
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
}
