package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.catchDefects
import dev.kioba.anchor.internal.safeExecute
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
  fun `CancellationException is fatal and rethrown even with defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    assertFailsWith<CancellationException> {
      catchDefects(anchor, anchor.defect) {
        throw CancellationException("cancelled")
      }
    }

    assertEquals(0, capturedDefects.size, "CancellationException must not reach defect handler")
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
