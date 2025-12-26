package dev.kioba.anchor

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.coroutineContext
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test to verify Job identity comparison behavior.
 * This helps understand whether coroutineContext[Job] matches the Job returned by launch.
 */
class JobIdentityTest {

  @Test
  fun `verify job identity - does coroutineContext Job match launched job`() = runBlocking {
    var launchedJob: Job? = null
    var contextJob: Job? = null
    var identityMatches: Boolean? = null

    val job = launch {
      contextJob = coroutineContext[Job]
      identityMatches = (launchedJob === coroutineContext[Job])
      delay(10)
    }

    launchedJob = job
    job.join()

    // Print for debugging
    println("Launched job: $launchedJob")
    println("Context job: $contextJob")
    println("Identity matches: $identityMatches")

    assertNotNull(contextJob, "Context job should not be null")
    assertNotNull(launchedJob, "Launched job should not be null")

    // This assertion will tell us if the identity comparison works
    assertTrue(
      identityMatches ?: false,
      "coroutineContext[Job] should match the Job returned by launch. " +
        "Launched: $launchedJob, Context: $contextJob, Match: $identityMatches"
    )
  }

  @Test
  fun `verify cleanup identity check in cancellable pattern`() = runBlocking {
    val anchor = AnchorRuntime<EmptyEffect, TestState>(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = null,
      subscriptions = null
    )

    // Execute a job
    anchor.cancellable("test") {
      delay(10)
    }

    // Wait for cleanup to happen
    delay(100)

    // If the identity check is broken, jobs.size would be 1
    // If it works correctly, jobs.size should be 0
    println("Jobs map size after cleanup: ${anchor.jobs.size}")
    println("Jobs map contents: ${anchor.jobs}")

    assertTrue(
      anchor.jobs.isEmpty(),
      "Jobs map should be empty after completion. " +
        "Size: ${anchor.jobs.size}, Contents: ${anchor.jobs}"
    )
  }
}
