package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.catchDefects
import dev.kioba.anchor.internal.safeExecute
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class NonFatalTest {

  private fun createAnchor(
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
  ): AnchorRuntime<EmptyEffect, TestState, Nothing> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      defect = defect,
    )

  @Test
  fun `the coroutine's own cancellation is rethrown even with defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })
    val started = CompletableDeferred<Unit>()
    val rethrown = CompletableDeferred<Throwable>()

    val job = launch {
      try {
        catchDefects(anchor, anchor.defect) {
          started.complete(Unit)
          awaitCancellation()
        }
      } catch (e: CancellationException) {
        rethrown.complete(e)
        throw e
      }
    }
    started.await()
    job.cancelAndJoin()

    assertEquals(Pair(true, 0), Pair(rethrown.isCompleted, capturedDefects.size))
  }

  @Test
  fun `RuntimeException is non-fatal and reaches defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    catchDefects(anchor, anchor.defect) {
      throw RuntimeException("boom")
    }

    assertEquals(1, capturedDefects.size)
    assertIs<RuntimeException>(capturedDefects.first())
  }

  @Test
  fun `IllegalStateException is non-fatal and reaches defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    catchDefects(anchor, anchor.defect) {
      throw IllegalStateException("bad state")
    }

    assertEquals(1, capturedDefects.size)
    assertIs<IllegalStateException>(capturedDefects.first())
  }

  @Test
  fun `non-fatal exception rethrows when no defect handler`(): Unit = runBlocking {
    val anchor = createAnchor(defect = null)

    assertFailsWith<RuntimeException> {
      catchDefects(anchor, anchor.defect) {
        throw RuntimeException("no handler")
      }
    }
  }

  @Test
  fun `safeExecute routes RaisedException to domain handler not defect handler`(): Unit = runBlocking {
    val capturedDomain = mutableListOf<TestError>()
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = AnchorRuntime<EmptyEffect, TestState, TestError>(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      onDomainError = { capturedDomain.add(it) },
      defect = { capturedDefects.add(it) },
    )

    safeExecute(anchor, anchor.onDomainError, anchor.defect) {
      throw RaisedException(TestError.NotFound)
    }

    assertEquals(1, capturedDomain.size)
    assertEquals(0, capturedDefects.size)
  }
}
