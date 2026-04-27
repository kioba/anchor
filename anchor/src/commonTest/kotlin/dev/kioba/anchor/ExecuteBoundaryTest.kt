package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.safeExecute
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ExecuteBoundaryTest {
  private fun createAnchor(
    init: (suspend Anchor<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
    onDomainError: (suspend ErrorScope<EmptyEffect, TestState>.(TestError) -> Unit)? = null,
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
  ): AnchorRuntime<EmptyEffect, TestState, TestError> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = init,
      subscriptions = subscriptions,
      onDomainError = onDomainError,
      defect = defect,
    )

  // -- execute {} path --

  @Test
  fun `raise in execute routes to onDomainError`(): Unit =
    runBlocking {
      val capturedErrors = mutableListOf<TestError>()
      val anchor =
        createAnchor(
          onDomainError = { capturedErrors.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.raise(TestError.NotFound)
      }

      assertEquals(1, capturedErrors.size)
      assertEquals(TestError.NotFound, capturedErrors.first())
    }

  @Test
  fun `orDie in execute routes to defect`(): Unit =
    runBlocking {
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          defect = { capturedDefects.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.orDie(TestError.NotFound)
      }

      assertEquals(1, capturedDefects.size)
      assertIs<DomainDefectException>(capturedDefects.first())
    }

  @Test
  fun `unexpected Throwable in execute routes to defect`(): Unit =
    runBlocking {
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          defect = { capturedDefects.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        throw RuntimeException("unexpected")
      }

      assertEquals(1, capturedDefects.size)
      assertIs<RuntimeException>(capturedDefects.first())
      assertEquals("unexpected", capturedDefects.first().message)
    }

  @Test
  fun `CancellationException in execute is never swallowed`(): Unit =
    runBlocking {
      val capturedErrors = mutableListOf<TestError>()
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          onDomainError = { capturedErrors.add(it) },
          defect = { capturedDefects.add(it) },
        )

      assertFailsWith<CancellationException> {
        safeExecute(anchor, anchor.onDomainError, anchor.defect) {
          throw CancellationException("cancelled")
        }
      }

      assertEquals(0, capturedErrors.size, "CancellationException must not reach onDomainError")
      assertEquals(0, capturedDefects.size, "CancellationException must not reach defect handler")
    }

  @Test
  fun `missing onDomainError causes rethrow`(): Unit =
    runBlocking {
      val anchor = createAnchor(onDomainError = null)

      assertFailsWith<RaisedException> {
        safeExecute(anchor, anchor.onDomainError, anchor.defect) {
          anchor.raise(TestError.NotFound)
        }
      }
    }

  @Test
  fun `missing defect handler causes rethrow`(): Unit =
    runBlocking {
      val anchor = createAnchor(defect = null)

      assertFailsWith<RuntimeException> {
        safeExecute(anchor, anchor.onDomainError, anchor.defect) {
          throw RuntimeException("no handler")
        }
      }
    }

  // -- init {} path --

  @Test
  fun `raise in init routes to onDomainError`(): Unit =
    runBlocking {
      val capturedErrors = mutableListOf<TestError>()
      val anchor =
        createAnchor(
          init = { raise(TestError.NotFound) },
          onDomainError = { capturedErrors.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
      }

      assertEquals(1, capturedErrors.size)
      assertEquals(TestError.NotFound, capturedErrors.first())
    }

  @Test
  fun `orDie in init routes to defect`(): Unit =
    runBlocking {
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          init = { orDie(TestError.Invalid("init defect")) },
          defect = { capturedDefects.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
      }

      assertEquals(1, capturedDefects.size)
      assertIs<DomainDefectException>(capturedDefects.first())
    }

  @Test
  fun `unexpected Throwable in init routes to defect`(): Unit =
    runBlocking {
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          init = { throw IllegalStateException("init boom") },
          defect = { capturedDefects.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
      }

      assertEquals(1, capturedDefects.size)
      assertIs<IllegalStateException>(capturedDefects.first())
      assertEquals("init boom", capturedDefects.first().message)
    }

  // -- subscription flow .catch path --

  @Test
  fun `RaisedException in subscription flow routes to onDomainError via catch`(): Unit =
    runBlocking {
      val capturedErrors = mutableListOf<TestError>()
      val anchor =
        createAnchor(
          onDomainError = { capturedErrors.add(it) },
        )

      val errorFlow = flow<Unit> { throw RaisedException(TestError.NotFound) }

      errorFlow
        .catch { e ->
          safeExecute(anchor, anchor.onDomainError, anchor.defect) {
            throw e
          }
        }.collect {}

      assertEquals(1, capturedErrors.size)
      assertEquals(TestError.NotFound, capturedErrors.first())
    }

  @Test
  fun `DomainDefectException in subscription flow routes to defect via catch`(): Unit =
    runBlocking {
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          defect = { capturedDefects.add(it) },
        )

      val errorFlow = flow<Unit> { throw DomainDefectException(TestError.NotFound) }

      errorFlow
        .catch { e ->
          safeExecute(anchor, anchor.onDomainError, anchor.defect) {
            throw e
          }
        }.collect {}

      assertEquals(1, capturedDefects.size)
      assertIs<DomainDefectException>(capturedDefects.first())
    }

  // -- cross-cutting guarantees --

  @Test
  fun `RaisedException never reaches defect handler`(): Unit =
    runBlocking {
      val capturedErrors = mutableListOf<TestError>()
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          onDomainError = { capturedErrors.add(it) },
          defect = { capturedDefects.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.raise(TestError.NotFound)
      }

      assertEquals(1, capturedErrors.size)
      assertEquals(0, capturedDefects.size, "RaisedException must not reach defect handler")
    }

  @Test
  fun `DomainDefectException never reaches onDomainError`(): Unit =
    runBlocking {
      val capturedErrors = mutableListOf<TestError>()
      val capturedDefects = mutableListOf<Throwable>()
      val anchor =
        createAnchor(
          onDomainError = { capturedErrors.add(it) },
          defect = { capturedDefects.add(it) },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.orDie(TestError.NotFound)
      }

      assertEquals(0, capturedErrors.size, "DomainDefectException must not reach onDomainError")
      assertEquals(1, capturedDefects.size)
    }

  @Test
  fun `handler can modify state during error recovery`(): Unit =
    runBlocking {
      val anchor =
        createAnchor(
          onDomainError = { error ->
            reduce { copy(value = -1) }
          },
        )

      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.reduce { copy(value = 42) }
        anchor.raise(TestError.NotFound)
      }

      assertEquals(-1, anchor.state.value, "onDomainError handler should have updated state")
    }
}
