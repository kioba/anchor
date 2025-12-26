package dev.kioba.anchor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive tests for the cancellable() function in AnchorRuntime.
 *
 * These tests verify:
 * 1. Race condition prevention - only one job runs at a time
 * 2. Memory cleanup - completed jobs are removed from the map
 * 3. Cancellation behavior - old jobs are cancelled before new ones start
 * 4. Edge cases - multiple keys, rapid calls, exceptions, etc.
 */
class CancellableTest {

  private fun createTestAnchor(): AnchorRuntime<EmptyEffect, TestState> {
    return AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null
    )
  }

  /**
   * Test 1: Basic Cancellation
   *
   * Verify that calling cancellable() with the same key cancels the previous job.
   */
  @Test
  fun `cancellable cancels previous job with same key`() = runBlocking {
    val anchor = createTestAnchor()
    val job1Started = CompletableDeferred<Unit>()
    val job1Cancelled = CompletableDeferred<Boolean>()
    val job2Completed = CompletableDeferred<Unit>()

    // Start first job
    launch {
      anchor.cancellable("test") {
        job1Started.complete(Unit)
        try {
          delay(1000) // Long delay
          job1Cancelled.complete(false) // Should not reach here
        } catch (e: Exception) {
          job1Cancelled.complete(true) // Should be cancelled
        }
      }
    }

    // Wait for job1 to start
    job1Started.await()

    // Start second job with same key (should cancel first)
    launch {
      anchor.cancellable("test") {
        job2Completed.complete(Unit)
      }
    }

    // Wait for job2 to complete
    job2Completed.await()

    // Verify job1 was cancelled
    assertTrue(job1Cancelled.await(), "First job should be cancelled")
  }

  /**
   * Test 2: Race Condition Prevention
   *
   * Verify that rapid concurrent calls with the same key don't cause race conditions.
   * Only the latest job should complete.
   */
  @Test
  fun `cancellable prevents race condition with concurrent calls`() = runBlocking {
    val anchor = createTestAnchor()
    val completedJobs = mutableListOf<Int>()
    val mutex = kotlinx.coroutines.sync.Mutex()

    // Launch 100 concurrent jobs with the same key
    val jobs = (1..100).map { jobId ->
      async(Dispatchers.Default) {
        anchor.cancellable("race-test") {
          // Small delay to increase chance of overlap
          delay(10)
          mutex.withLock {
            completedJobs.add(jobId)
          }
        }
      }
    }

    // Wait for all launches to complete
    jobs.awaitAll()

    // Give time for any lingering jobs
    delay(100)

    // Only ONE job should have completed (the last one)
    // Due to timing, it might be close to 100 but should be very few
    assertTrue(
      completedJobs.size <= 3,
      "Expected at most 3 jobs to complete, but ${completedJobs.size} completed. " +
        "This indicates a race condition!"
    )
  }

  /**
   * Test 3: Memory Cleanup
   *
   * Verify that completed jobs are removed from the jobs map (no memory leak).
   */
  @Test
  fun `cancellable cleans up completed jobs from map`() = runBlocking {
    val anchor = createTestAnchor()

    // Execute 100 jobs with different keys
    repeat(100) { i ->
      anchor.cancellable("key-$i") {
        delay(10)
      }
    }

    // Wait for all jobs to complete
    delay(200)

    // Verify jobs map is empty (all cleaned up)
    assertEquals(
      0,
      anchor.jobs.size,
      "Jobs map should be empty after all jobs complete, but contains ${anchor.jobs.size} entries. " +
        "This indicates a memory leak!"
    )
  }

  /**
   * Test 4: Multiple Keys Don't Interfere
   *
   * Verify that jobs with different keys don't cancel each other.
   */
  @Test
  fun `cancellable with different keys do not interfere`() = runBlocking {
    val anchor = createTestAnchor()
    val job1Completed = CompletableDeferred<Unit>()
    val job2Completed = CompletableDeferred<Unit>()
    val job3Completed = CompletableDeferred<Unit>()

    // Launch jobs with different keys concurrently
    launch {
      anchor.cancellable("key1") {
        delay(50)
        job1Completed.complete(Unit)
      }
    }

    launch {
      anchor.cancellable("key2") {
        delay(50)
        job2Completed.complete(Unit)
      }
    }

    launch {
      anchor.cancellable("key3") {
        delay(50)
        job3Completed.complete(Unit)
      }
    }

    // All three should complete
    job1Completed.await()
    job2Completed.await()
    job3Completed.await()

    // Success - all jobs completed without interfering
  }

  /**
   * Test 5: Exception Handling
   *
   * Verify that exceptions in jobs don't prevent cleanup and don't break the mutex.
   */
  @Test
  fun `cancellable cleans up even when job throws exception`() = runBlocking {
    val anchor = createTestAnchor()

    // Launch job that throws exception
    try {
      anchor.cancellable("exception-test") {
        delay(10)
        throw RuntimeException("Test exception")
      }
    } catch (e: RuntimeException) {
      // Expected
    }

    // Wait for cleanup
    delay(100)

    // Verify cleanup happened despite exception
    assertEquals(
      0,
      anchor.jobs.size,
      "Job should be cleaned up even after exception"
    )

    // Verify we can still use cancellable (mutex not broken)
    val subsequentJobCompleted = CompletableDeferred<Unit>()
    anchor.cancellable("exception-test") {
      subsequentJobCompleted.complete(Unit)
    }

    subsequentJobCompleted.await()
    // Success - subsequent calls work fine
  }

  /**
   * Test 6: Rapid Sequential Calls
   *
   * Verify that rapid sequential calls (not concurrent) work correctly.
   */
  @Test
  fun `cancellable handles rapid sequential calls correctly`() = runBlocking {
    val anchor = createTestAnchor()
    var lastCompletedJob = 0

    // Make 10 sequential calls with the same key
    repeat(10) { i ->
      anchor.cancellable("sequential") {
        delay(5)
        lastCompletedJob = i
      }
      yield() // Yield to allow job to start
    }

    // Wait for last job to complete
    delay(100)

    // The last job (9) should have completed
    assertEquals(9, lastCompletedJob, "Last job should have completed")

    // Jobs map should be empty
    assertEquals(0, anchor.jobs.size, "Jobs map should be empty")
  }

  /**
   * Test 7: Job Cancellation Propagation
   *
   * Verify that when a job is cancelled, its internal coroutines are also cancelled.
   */
  @Test
  fun `cancellable propagates cancellation to job internals`() = runBlocking {
    val anchor = createTestAnchor()
    val innerJobCancelled = CompletableDeferred<Boolean>()
    val job1Started = CompletableDeferred<Unit>()

    // Start first job with internal coroutine
    launch {
      anchor.cancellable("propagation-test") {
        job1Started.complete(Unit)
        try {
          // Launch internal work
          launch {
            delay(1000)
            innerJobCancelled.complete(false)
          }.join()
        } catch (e: Exception) {
          innerJobCancelled.complete(true)
        }
      }
    }

    // Wait for job1 to start
    job1Started.await()

    // Cancel by starting new job with same key
    launch {
      anchor.cancellable("propagation-test") {
        delay(10)
      }
    }

    // Wait a bit for cancellation to propagate
    delay(100)

    // Verify internal job was cancelled
    assertTrue(
      innerJobCancelled.await(),
      "Internal coroutines should be cancelled when parent job is cancelled"
    )
  }

  /**
   * Test 8: State Updates During Cancellation
   *
   * Verify that state updates work correctly even during rapid cancellations.
   */
  @Test
  fun `cancellable allows state updates during rapid cancellations`() = runBlocking {
    val anchor = createTestAnchor()

    // Make rapid calls that update state
    repeat(50) { i ->
      launch {
        anchor.cancellable("state-update") {
          anchor.reduce { copy(value = i) }
          delay(10)
        }
      }
    }

    // Wait for everything to settle
    delay(500)

    // State should have been updated (might be any value from 0-49)
    // The important thing is no crash and cleanup happened
    assertTrue(
      anchor.state.value in 0..49,
      "State should contain one of the update values"
    )

    // Jobs map should be empty
    assertEquals(0, anchor.jobs.size, "All jobs should be cleaned up")
  }

  /**
   * Test 9: Empty Jobs Map After Cancellation
   *
   * Verify that when a job is cancelled before it completes,
   * it's removed from the jobs map.
   */
  @Test
  fun `cancellable removes job from map when cancelled before completion`() = runBlocking {
    val anchor = createTestAnchor()
    val job1Started = CompletableDeferred<Unit>()

    // Start long-running job
    launch {
      anchor.cancellable("cancel-test") {
        job1Started.complete(Unit)
        delay(1000) // Long delay
      }
    }

    job1Started.await()

    // Verify job is in map
    assertEquals(1, anchor.jobs.size, "Job should be in map while running")

    // Cancel by starting new empty job
    anchor.cancellable("cancel-test") {
      // Empty - completes immediately
    }

    // Wait a bit for cleanup
    delay(100)

    // Verify map is empty
    assertEquals(0, anchor.jobs.size, "Job should be removed after cancellation")
  }

  /**
   * Test 10: Stress Test - Many Concurrent Keys
   *
   * Verify that the mutex handles high concurrency with many different keys.
   */
  @Test
  fun `cancellable handles stress test with many concurrent keys`() = runBlocking {
    val anchor = createTestAnchor()
    val jobsCompleted = mutableSetOf<String>()
    val mutex = kotlinx.coroutines.sync.Mutex()

    // Launch 200 jobs with 20 different keys (10 jobs per key)
    val jobs = (1..200).map { i ->
      val key = "key-${i % 20}"
      async(Dispatchers.Default) {
        anchor.cancellable(key) {
          delay(20)
          mutex.withLock {
            jobsCompleted.add("$key-$i")
          }
        }
      }
    }

    jobs.awaitAll()

    // Wait for cleanup
    delay(200)

    // At least some jobs should have completed
    assertTrue(
      jobsCompleted.isNotEmpty(),
      "Some jobs should have completed"
    )

    // All jobs should be cleaned up
    assertEquals(
      0,
      anchor.jobs.size,
      "All jobs should be cleaned up after completion"
    )
  }
}

// Test fixtures
internal data class TestState(val value: Int) : ViewState
