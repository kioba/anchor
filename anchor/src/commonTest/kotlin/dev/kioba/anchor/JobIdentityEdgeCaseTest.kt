package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests edge cases where job identity comparison might behave unexpectedly.
 *
 * They run on `runTest`'s virtual time, so the timing windows below (such as
 * "10ms before job 1 finishes") are exact rather than best-effort.
 */
class JobIdentityEdgeCaseTest {
  private fun createTestAnchor(): AnchorRuntime<EmptyEffect, TestState, Nothing> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null,
    )

  @Test
  fun `rapid fire cancellable calls - cleanup race condition test`() =
    runTest {
      val anchor = createTestAnchor()
      val jobsCompleted = mutableListOf<Int>()

      // Launch 50 rapid calls with the same key
      val callers =
        List(50) { i ->
          launch {
            anchor.cancellable("rapid-test") {
              delay(5)
              jobsCompleted.add(i)
            }
          }
        }

      // Wait for every caller to return
      callers.joinAll()

      println("Jobs completed: ${jobsCompleted.size}")
      println("Jobs map size: ${anchor.jobs.size}")
      println("Jobs map contents: ${anchor.jobs.keys}")

      // Each call cancelled its predecessor before that one ran
      assertEquals(listOf(49), jobsCompleted, "Only the latest call should complete")

      // CRITICAL: If cleanup identity check is broken, jobs would accumulate
      assertEquals(
        0,
        anchor.jobs.size,
        "Jobs map should be empty. If not, identity comparison is broken! " +
          "Completed: ${jobsCompleted.size}, Map size: ${anchor.jobs.size}",
      )
    }

  @Test
  fun `sequential cancellable with same key - verify no accumulation`() =
    runTest {
      val anchor = createTestAnchor()

      // Run 100 sequential cancellable operations. Each call returns once its
      // job has completed and cleaned up.
      repeat(100) { i ->
        anchor.cancellable("sequential-$i") {
          delay(5)
        }
      }

      println("After 100 sequential calls - Jobs map size: ${anchor.jobs.size}")

      assertEquals(
        0,
        anchor.jobs.size,
        "All jobs should be cleaned up. Size: ${anchor.jobs.size}",
      )
    }

  @Test
  fun `nested cancellable calls - different keys`() =
    runTest {
      val anchor = createTestAnchor()

      anchor.cancellable("outer") {
        delay(10)
        // Nested call with different key
        anchor.cancellable("inner") {
          delay(10)
        }
      }

      println("After nested calls - Jobs map size: ${anchor.jobs.size}")

      assertEquals(
        0,
        anchor.jobs.size,
        "Both outer and inner jobs should be cleaned up. Size: ${anchor.jobs.size}",
      )
    }

  @Test
  fun `job cleanup race - job completes while new one starts`() =
    runTest {
      val anchor = createTestAnchor()
      val job1Started = CompletableDeferred<Unit>()
      val job1NearComplete = CompletableDeferred<Unit>()
      var job2OwnedEntry = false

      // Start first job
      launch {
        anchor.cancellable("race") {
          job1Started.complete(Unit)
          delay(50)
          job1NearComplete.complete(Unit) // About to finish
        }
      }

      job1Started.await()
      delay(40) // Job 1 is almost done (10ms left)

      // Start second job right before first completes
      anchor.cancellable("race") {
        // Job 1 has been cancelled and has run its cleanup by now. With a
        // working identity check, that cleanup left job 2's entry alone.
        job2OwnedEntry = anchor.jobs["race"]?.job === currentCoroutineContext()[Job]
        delay(10)
      }

      println("After race condition test - Jobs map size: ${anchor.jobs.size}")
      println("Jobs map: ${anchor.jobs}")

      assertFalse(job1NearComplete.isCompleted, "Job 1 should be cancelled 10ms before it finishes")

      // THIS is where identity check matters!
      // If job1's cleanup removes job2, map would be empty but for wrong reason
      // If identity check works, only the correct job is removed
      assertTrue(job2OwnedEntry, "Job 1's cleanup must not remove job 2's entry")
      assertEquals(
        0,
        anchor.jobs.size,
        "Should be empty after both jobs complete. Size: ${anchor.jobs.size}",
      )
    }
}
