package dev.kioba.anchor
import dev.kioba.anchor.internal.AnchorRuntime

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Basic smoke tests for the cancellable() function.
 * These tests run quickly to verify core functionality: they use runTest's
 * virtual time, so their delays cost no wall-clock time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancellableBasicTest {

  private fun createTestAnchor(): AnchorRuntime<EmptyEffect, TestState, Nothing> {
    return AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null
    )
  }

  @Test
  fun `cancellable cancels previous job`() = runTest {
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
        } catch (e: CancellationException) {
          job1Cancelled.complete(true)
          throw e
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
    assertEquals(0, currentTime, "Second job should not wait out the first job's delay")
  }

  @Test
  fun `cancellable cleans up completed jobs`() = runTest {
    val anchor = createTestAnchor()

    // Execute job; the call returns once the job has completed and cleaned up
    anchor.cancellable("test") {
      delay(10)
    }

    // Verify cleanup
    assertEquals(0, anchor.jobs.size, "Jobs map should be empty after completion")
  }

  @Test
  fun `cancellable with different keys dont interfere`() = runTest {
    val anchor = createTestAnchor()
    val job1Completed = CompletableDeferred<Unit>()
    val job2Completed = CompletableDeferred<Unit>()

    val caller1 = launch {
      anchor.cancellable("key1") {
        delay(20)
        job1Completed.complete(Unit)
      }
    }

    val caller2 = launch {
      anchor.cancellable("key2") {
        delay(20)
        job2Completed.complete(Unit)
      }
    }

    joinAll(caller1, caller2)

    // Both should complete, side by side rather than one after another
    assertTrue(job1Completed.isCompleted, "key1 job should complete")
    assertTrue(job2Completed.isCompleted, "key2 job should complete")
    assertEquals(20, currentTime, "Jobs with different keys should run concurrently")
  }

  @Test
  fun `cancellable cleans up after exception`() = runTest {
    val anchor = createTestAnchor()

    // The block's exception propagates to the caller
    assertFailsWith<RuntimeException> {
      anchor.cancellable("test") {
        delay(10)
        throw RuntimeException("Test exception")
      }
    }

    assertEquals(0, anchor.jobs.size, "Job should be cleaned up after exception")
  }
}
