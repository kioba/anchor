package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
 * Comprehensive tests for the cancellable() function in AnchorRuntime.
 *
 * These tests verify:
 * 1. Race condition prevention - only one job runs at a time
 * 2. Memory cleanup - completed jobs are removed from the map
 * 3. Cancellation behavior - old jobs are cancelled before new ones start
 * 4. Edge cases - multiple keys, rapid calls, exceptions, etc.
 *
 * The tests run on `runTest`'s virtual time. `cancellable` launches its job in the
 * caller's scope, so every block and every `delay` runs on the test dispatcher:
 * delays cost no wall-clock time, interleavings are deterministic, and
 * `currentTime` shows whether a cancelled job was cut short or waited out.
 * Contention across real threads is covered by the JVM-only
 * `CancellableKeyIsolationTest` in `desktopTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CancellableTest {
  private fun createTestAnchor(): AnchorRuntime<EmptyEffect, TestState, Nothing> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null,
    )

  /**
   * Test 1: Basic Cancellation
   *
   * Verify that calling cancellable() with the same key cancels the previous job.
   */
  @Test
  fun `cancellable cancels previous job with same key`() =
    runTest {
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
          } catch (e: CancellationException) {
            job1Cancelled.complete(true) // Should be cancelled
            throw e
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

      // Verify job1 was cancelled rather than waited out
      assertTrue(job1Cancelled.await(), "First job should be cancelled")
      assertEquals(0, currentTime, "Second job should not wait out the first job's delay")
    }

  /**
   * Test 2: Race Condition Prevention
   *
   * Verify that rapid concurrent calls with the same key don't cause race conditions.
   * Only the latest job should complete.
   */
  @Test
  fun `cancellable prevents race condition with concurrent calls`() =
    runTest {
      val anchor = createTestAnchor()
      val completedJobs = mutableListOf<Int>()

      // Launch 100 concurrent jobs with the same key. They interleave on the test
      // dispatcher, so each call cancels its predecessor before that one runs.
      val jobs =
        (1..100).map { jobId ->
          async {
            anchor.cancellable("race-test") {
              delay(10)
              completedJobs.add(jobId)
            }
          }
        }

      // Wait for all launches to complete
      jobs.awaitAll()

      // Only ONE job should have completed: the last one
      assertEquals(
        listOf(100),
        completedJobs,
        "Expected only the last job to complete, but $completedJobs completed. " +
          "This indicates a race condition!",
      )
      assertEquals(0, anchor.jobs.size, "Jobs map should be empty")
    }

  /**
   * Test 3: Memory Cleanup
   *
   * Verify that completed jobs are removed from the jobs map (no memory leak).
   */
  @Test
  fun `cancellable cleans up completed jobs from map`() =
    runTest {
      val anchor = createTestAnchor()

      // Execute 100 jobs with different keys. Each call returns only after its
      // job has completed and removed its own entry.
      repeat(100) { i ->
        anchor.cancellable("key-$i") {
          delay(10)
        }
      }

      // Verify jobs map is empty (all cleaned up)
      assertEquals(
        0,
        anchor.jobs.size,
        "Jobs map should be empty after all jobs complete, but contains ${anchor.jobs.size} entries. " +
          "This indicates a memory leak!",
      )
    }

  /**
   * Test 4: Multiple Keys Don't Interfere
   *
   * Verify that jobs with different keys don't cancel each other.
   */
  @Test
  fun `cancellable with different keys do not interfere`() =
    runTest {
      val anchor = createTestAnchor()
      val job1Completed = CompletableDeferred<Unit>()
      val job2Completed = CompletableDeferred<Unit>()
      val job3Completed = CompletableDeferred<Unit>()

      // Launch jobs with different keys concurrently
      val caller1 =
        launch {
          anchor.cancellable("key1") {
            delay(50)
            job1Completed.complete(Unit)
          }
        }

      val caller2 =
        launch {
          anchor.cancellable("key2") {
            delay(50)
            job2Completed.complete(Unit)
          }
        }

      val caller3 =
        launch {
          anchor.cancellable("key3") {
            delay(50)
            job3Completed.complete(Unit)
          }
        }

      joinAll(caller1, caller2, caller3)

      // All three should complete, side by side rather than one after another
      assertTrue(job1Completed.isCompleted, "key1 job should complete")
      assertTrue(job2Completed.isCompleted, "key2 job should complete")
      assertTrue(job3Completed.isCompleted, "key3 job should complete")
      assertEquals(50, currentTime, "Jobs with different keys should run concurrently")
    }

  /**
   * Test 5: Exception Handling
   *
   * Verify that exceptions in jobs don't prevent cleanup and don't break the mutex.
   */
  @Test
  fun `cancellable cleans up even when job throws exception`() =
    runTest {
      val anchor = createTestAnchor()

      // Launch job that throws exception; it propagates to the caller
      assertFailsWith<RuntimeException> {
        anchor.cancellable("exception-test") {
          delay(10)
          throw RuntimeException("Test exception")
        }
      }

      // Verify cleanup happened despite exception
      assertEquals(
        0,
        anchor.jobs.size,
        "Job should be cleaned up even after exception",
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
  fun `cancellable handles rapid sequential calls correctly`() =
    runTest {
      val anchor = createTestAnchor()
      val completedJobs = mutableListOf<Int>()

      // Make 10 sequential calls with the same key. Each call waits for its own
      // job, so no call finds a running predecessor to cancel.
      repeat(10) { i ->
        anchor.cancellable("sequential") {
          delay(5)
          completedJobs.add(i)
        }
      }

      // Every job, up to the last one (9), should have completed in order
      assertEquals((0..9).toList(), completedJobs, "Every sequential job should complete")

      // Jobs map should be empty
      assertEquals(0, anchor.jobs.size, "Jobs map should be empty")
    }

  /**
   * Test 7: Job Cancellation Propagation
   *
   * Verify that when a job is cancelled, its internal coroutines are also cancelled.
   */
  @Test
  fun `cancellable propagates cancellation to job internals`() =
    runTest {
      val anchor = createTestAnchor()
      val innerJobCancelled = CompletableDeferred<Boolean>()
      val innerJobStarted = CompletableDeferred<Unit>()

      // Start first job with internal coroutine. coroutineScope makes the internal
      // coroutine a child of the cancellable job, not of this test's launch.
      launch {
        anchor.cancellable("propagation-test") {
          coroutineScope {
            // Launch internal work
            launch {
              innerJobStarted.complete(Unit)
              try {
                delay(1000)
                innerJobCancelled.complete(false)
              } catch (e: CancellationException) {
                innerJobCancelled.complete(true)
                throw e
              }
            }
          }
        }
      }

      // Wait for the internal coroutine to start
      innerJobStarted.await()

      // Cancel by starting new job with same key
      launch {
        anchor.cancellable("propagation-test") {
          delay(10)
        }
      }

      // Verify internal job was cancelled
      assertTrue(
        innerJobCancelled.await(),
        "Internal coroutines should be cancelled when parent job is cancelled",
      )
    }

  /**
   * Test 8: State Updates During Cancellation
   *
   * Verify that state updates work correctly even during rapid cancellations.
   */
  @Test
  fun `cancellable allows state updates during rapid cancellations`() =
    runTest {
      val anchor = createTestAnchor()

      // Make rapid calls that update state
      val callers =
        List(50) { i ->
          launch {
            anchor.cancellable("state-update") {
              anchor.reduce { copy(value = i) }
              delay(10)
            }
          }
        }

      // Wait for every caller to return
      callers.joinAll()

      // Each call cancelled its predecessor before that one ran, so only one
      // 10ms block ran and the state holds the latest call's update
      assertEquals(10, currentTime, "Rapid calls should cancel each other")
      assertEquals(49, anchor.state.value, "State should hold the latest update")

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
  fun `cancellable removes job from map when cancelled before completion`() =
    runTest {
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

      // Cancel by starting new empty job; the call returns once it has completed
      anchor.cancellable("cancel-test") {
        // Empty - completes immediately
      }

      // Verify map is empty, and the first job was cut short rather than waited out
      assertEquals(0, anchor.jobs.size, "Job should be removed after cancellation")
      assertEquals(0, currentTime, "First job should be cancelled, not waited out")
    }

  /**
   * Test 10: Stress Test - Many Concurrent Keys
   *
   * Verify that the mutex handles high concurrency with many different keys.
   */
  @Test
  fun `cancellable handles stress test with many concurrent keys`() =
    runTest {
      val anchor = createTestAnchor()
      val jobsCompleted = mutableSetOf<String>()

      // Launch 200 jobs with 20 different keys (10 jobs per key)
      val jobs =
        (1..200).map { i ->
          val key = "key-${i % 20}"
          async {
            anchor.cancellable(key) {
              delay(20)
              jobsCompleted.add("$key-$i")
            }
          }
        }

      jobs.awaitAll()

      // Exactly one job per key should have completed: the latest call for that key
      assertEquals(
        (181..200).map { i -> "key-${i % 20}-$i" }.toSet(),
        jobsCompleted,
        "Only the latest job for each key should complete",
      )

      // The keys ran side by side: one 20ms window, not one per key
      assertEquals(20, currentTime, "Jobs with different keys should run concurrently")

      // All jobs should be cleaned up
      assertEquals(
        0,
        anchor.jobs.size,
        "All jobs should be cleaned up after completion",
      )
    }
}

// Test fixtures
internal data class TestState(
  val value: Int,
) : ViewState
