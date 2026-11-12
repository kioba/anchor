package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests for `cancellable` key isolation (#145, plans/026).
 *
 * These tests deliberately use real threads, wall-clock timing and blocking
 * `Thread.sleep`, so they are JVM-only and live in `desktopTest`. They are
 * documented exceptions to plans/014's virtual-time migration of the
 * cancellable tests: the bugs they guard against (a slow or non-cooperative
 * job on one key stalling another key, a cancelled waiter letting a third
 * call overlap a still-running predecessor) only show up under real
 * parallelism and cannot be reproduced on a virtual-time scheduler.
 */
class CancellableKeyIsolationTest {
  private fun runtime(): AnchorRuntime<EmptyEffect, TestState, Nothing> =
    AnchorRuntime(initialState = { TestState(value = 0) }, effectScope = { EmptyEffect })

  @Test
  fun `at most one block per key runs concurrently`() =
    runBlocking {
      val anchor = runtime()
      val running = AtomicInteger(0)
      val maxSeen = AtomicInteger(0)
      (1..200).map {
        async(Dispatchers.Default) {
          anchor.cancellable("search") {
            val now = running.incrementAndGet()
            maxSeen.updateAndGet { m -> maxOf(m, now) }
            try {
              delay(2)
            } finally {
              running.decrementAndGet()
            }
          }
        }
      }.awaitAll()
      assertEquals(1, maxSeen.get(), "more than one cancellable block ran concurrently")
      assertEquals(0, anchor.jobs.size)
    }

  @Test
  fun `unique keys do not accumulate`() =
    runBlocking {
      val anchor = runtime()
      (1..1000).map { i -> async(Dispatchers.Default) { anchor.cancellable("q$i") { delay(1) } } }.awaitAll()
      assertEquals(0, anchor.jobs.size)
    }

  @Test
  fun `job cancelled from outside during mutex contention leaves no entry`() =
    runBlocking {
      val anchor = runtime()
      val k2Started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("k2") {
            try {
              k2Started.complete(Unit)
              awaitCancellation()
            } finally {
              withContext(NonCancellable) { delay(300) }
            }
          }
        }
      k2Started.await()
      val k1Started = CompletableDeferred<Unit>()
      val k1Caller =
        launch(Dispatchers.Default) {
          anchor.cancellable("k1") {
            k1Started.complete(Unit)
            awaitCancellation()
          }
        }
      k1Started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("k2") { } }
      delay(50)
      k1Caller.cancelAndJoin() // e.g. an outer cancellable / screen teardown cancels the caller
      second.join()
      first.join()
      assertTrue(anchor.jobs.isEmpty(), "stale entries left in jobs map: ${anchor.jobs.keys}")
    }

  @Test
  fun `slow cancellation cleanup on one key does not delay an unrelated key`() =
    runBlocking {
      val anchor = runtime()
      val started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("slow") {
            try {
              started.complete(Unit)
              awaitCancellation()
            } finally {
              withContext(NonCancellable) { delay(400) }
            }
          }
        }
      started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("slow") { } }
      delay(50)
      val t0 = System.nanoTime()
      val entered = CompletableDeferred<Long>()
      launch(Dispatchers.Default) {
        anchor.cancellable("other") { entered.complete((System.nanoTime() - t0) / 1_000_000) }
      }
      val waitedMs = entered.await()
      second.join()
      first.join()
      assertTrue(waitedMs < 100, "unrelated key blocked for ${waitedMs}ms")
    }

  @Test
  fun `blocking effect on one key does not stall an unrelated key`() =
    runBlocking {
      val anchor = runtime()
      val started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("search") {
            effect {
              started.complete(Unit)
              Thread.sleep(400) // blocking client call: not interruptible by cancellation
            }
          }
        }
      started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("search") { } }
      delay(50)
      val t0 = System.nanoTime()
      val entered = CompletableDeferred<Long>()
      launch(Dispatchers.Default) {
        anchor.cancellable("refresh") { entered.complete((System.nanoTime() - t0) / 1_000_000) }
      }
      val waitedMs = entered.await()
      second.join()
      first.join()
      assertTrue(waitedMs < 100, "unrelated key blocked for ${waitedMs}ms")
    }

  @Test
  fun `cancelling a caller queued behind a slow predecessor is prompt`() =
    runBlocking {
      val anchor = runtime()
      val started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            effect {
              started.complete(Unit)
              Thread.sleep(400)
            }
          }
        }
      started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("k") { } }
      delay(50)
      val t0 = System.nanoTime()
      second.cancelAndJoin()
      val ms = (System.nanoTime() - t0) / 1_000_000
      first.join()
      assertTrue(ms < 100, "cancelling a queued caller took ${ms}ms")
    }

  @Test
  fun `cancelled queued caller does not let a third call overlap the predecessor`() =
    runBlocking {
      val anchor = runtime()
      val running = AtomicInteger(0)
      val maxSeen = AtomicInteger(0)

      fun enter() {
        val now = running.incrementAndGet()
        maxSeen.updateAndGet { m -> maxOf(m, now) }
      }
      val started = CompletableDeferred<Unit>()
      val a =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            effect {
              enter()
              started.complete(Unit)
              try {
                Thread.sleep(400) // blocking: ignores coroutine cancellation
              } finally {
                running.decrementAndGet()
              }
            }
          }
        }
      started.await()
      val b =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            enter()
            running.decrementAndGet()
          }
        }
      delay(50)
      b.cancelAndJoin()
      val c =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            enter()
            try {
              delay(20)
            } finally {
              running.decrementAndGet()
            }
          }
        }
      c.join()
      a.join()
      assertEquals(1, maxSeen.get(), "two blocks for key k ran at the same time")
    }
}
