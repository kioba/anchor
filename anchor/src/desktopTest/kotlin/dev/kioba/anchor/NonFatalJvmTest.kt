package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.catchDefects
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NonFatalJvmTest {

  private fun createAnchor(
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
  ): AnchorRuntime<EmptyEffect, TestState, Nothing> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      defect = defect,
    )

  @Test
  fun `OutOfMemoryError is fatal and rethrown even with defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    assertFailsWith<OutOfMemoryError> {
      catchDefects(anchor, anchor.defect) {
        throw OutOfMemoryError("oom")
      }
    }

    assertEquals(0, capturedDefects.size)
  }

  @Test
  fun `StackOverflowError is fatal and rethrown even with defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    assertFailsWith<StackOverflowError> {
      catchDefects(anchor, anchor.defect) {
        throw StackOverflowError("stack overflow")
      }
    }

    assertEquals(0, capturedDefects.size)
  }

  @Test
  fun `LinkageError is fatal and rethrown even with defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    assertFailsWith<LinkageError> {
      catchDefects(anchor, anchor.defect) {
        throw LinkageError("linkage")
      }
    }

    assertEquals(0, capturedDefects.size)
  }

  @Test
  fun `InterruptedException is fatal and rethrown even with defect handler`(): Unit = runBlocking {
    val capturedDefects = mutableListOf<Throwable>()
    val anchor = createAnchor(defect = { capturedDefects.add(it) })

    assertFailsWith<InterruptedException> {
      catchDefects(anchor, anchor.defect) {
        throw InterruptedException("interrupted")
      }
    }

    assertEquals(0, capturedDefects.size)
  }
}
