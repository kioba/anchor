package dev.kioba.anchor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Basic smoke tests for the cancellable() function.
 * These tests run quickly to verify core functionality.
 */
class CancellableBasicTest {

  private fun createTestAnchor(): AnchorRuntime<EmptyEffect, TestState> {
    return AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null
    )
  }

  @Test
  fun `cancellable cancels previous job`() = runBlocking {
    val anchor = createTestAnchor()
    val job1Started = CompletableDeferred<Unit>()
    val job1Cancelled = CompletableDeferred<Boolean>()
    val job2Completed = CompletableDeferred<Unit>()

    // Start first job
    launch {
      anchor.cancellable("test") {
        job1Started.complete(Unit)
        try {
          delay(100) // Short delay
          job1Cancelled.complete(false)
        } catch (e: Exception) {
          job1Cancelled.complete(true)
        }
      }
    }

    job1Started.await()

    // Start second job (should cancel first)
    launch {
      anchor.cancellable("test") {
        job2Completed.complete(Unit)
      }
    }

    job2Completed.await()
    assertTrue(job1Cancelled.await(), "First job should be cancelled")
  }

  @Test
  fun `cancellable cleans up completed jobs`() = runBlocking {
    val anchor = createTestAnchor()

    // Execute job
    anchor.cancellable("test") {
      delay(10)
    }

    // Wait for cleanup
    delay(50)

    // Verify cleanup
    assertEquals(0, anchor.jobs.size, "Jobs map should be empty after completion")
  }

  @Test
  fun `cancellable with different keys dont interfere`() = runBlocking {
    val anchor = createTestAnchor()
    val job1Completed = CompletableDeferred<Unit>()
    val job2Completed = CompletableDeferred<Unit>()

    launch {
      anchor.cancellable("key1") {
        delay(20)
        job1Completed.complete(Unit)
      }
    }

    launch {
      anchor.cancellable("key2") {
        delay(20)
        job2Completed.complete(Unit)
      }
    }

    // Both should complete
    job1Completed.await()
    job2Completed.await()
  }

  @Test
  fun `cancellable cleans up after exception`() = runBlocking {
    val anchor = createTestAnchor()

    try {
      anchor.cancellable("test") {
        delay(10)
        throw RuntimeException("Test exception")
      }
    } catch (e: RuntimeException) {
      // Expected
    }

    delay(50)
    assertEquals(0, anchor.jobs.size, "Job should be cleaned up after exception")
  }
}
