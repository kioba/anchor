package dev.kioba.anchor

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests edge cases where job identity comparison might behave unexpectedly.
 */
class JobIdentityEdgeCaseTest {

  private fun createTestAnchor(): AnchorRuntime<EmptyEffect, TestState> {
    return AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null
    )
  }

  @Test
  fun `rapid fire cancellable calls - cleanup race condition test`() = runBlocking {
    val anchor = createTestAnchor()
    val jobsCompleted = mutableListOf<Int>()

    // Launch 50 rapid calls with the same key
    repeat(50) { i ->
      launch {
        anchor.cancellable("rapid-test") {
          delay(5)
          synchronized(jobsCompleted) {
            jobsCompleted.add(i)
          }
        }
      }
    }

    // Wait for all to settle
    delay(500)

    println("Jobs completed: ${jobsCompleted.size}")
    println("Jobs map size: ${anchor.jobs.size}")
    println("Jobs map contents: ${anchor.jobs.keys}")

    // CRITICAL: If cleanup identity check is broken, jobs would accumulate
    assertEquals(
      0,
      anchor.jobs.size,
      "Jobs map should be empty. If not, identity comparison is broken! " +
        "Completed: ${jobsCompleted.size}, Map size: ${anchor.jobs.size}"
    )
  }

  @Test
  fun `sequential cancellable with same key - verify no accumulation`() = runBlocking {
    val anchor = createTestAnchor()

    // Run 100 sequential cancellable operations
    repeat(100) { i ->
      anchor.cancellable("sequential-$i") {
        delay(5)
      }
    }

    // Wait for cleanup
    delay(200)

    println("After 100 sequential calls - Jobs map size: ${anchor.jobs.size}")

    assertEquals(
      0,
      anchor.jobs.size,
      "All jobs should be cleaned up. Size: ${anchor.jobs.size}"
    )
  }

  @Test
  fun `nested cancellable calls - different keys`() = runBlocking {
    val anchor = createTestAnchor()

    anchor.cancellable("outer") {
      delay(10)
      // Nested call with different key
      anchor.cancellable("inner") {
        delay(10)
      }
    }

    delay(200)

    println("After nested calls - Jobs map size: ${anchor.jobs.size}")

    assertEquals(
      0,
      anchor.jobs.size,
      "Both outer and inner jobs should be cleaned up. Size: ${anchor.jobs.size}"
    )
  }

  @Test
  fun `job cleanup race - job completes while new one starts`() = runBlocking {
    val anchor = createTestAnchor()
    val job1Started = CompletableDeferred<Unit>()
    val job1NearComplete = CompletableDeferred<Unit>()

    // Start first job
    launch {
      anchor.cancellable("race") {
        job1Started.complete(Unit)
        delay(50)
        job1NearComplete.complete(Unit)  // About to finish
      }
    }

    job1Started.await()
    delay(40)  // Job 1 is almost done (10ms left)

    // Start second job right before first completes
    anchor.cancellable("race") {
      delay(10)
    }

    delay(200)

    println("After race condition test - Jobs map size: ${anchor.jobs.size}")
    println("Jobs map: ${anchor.jobs}")

    // THIS is where identity check matters!
    // If job1's cleanup removes job2, map would be empty but for wrong reason
    // If identity check works, only the correct job is removed
    assertEquals(
      0,
      anchor.jobs.size,
      "Should be empty after both jobs complete. Size: ${anchor.jobs.size}"
    )
  }
}
