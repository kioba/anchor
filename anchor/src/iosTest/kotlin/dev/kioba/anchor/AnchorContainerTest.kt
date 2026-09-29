package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSDate
import platform.Foundation.NSDefaultRunLoopMode
import platform.Foundation.NSRunLoop
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.runMode
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference
import kotlin.native.runtime.GC
import kotlin.native.runtime.NativeRuntimeApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Tests run on the main thread, like a Swift caller. Container collectors deliver on `Dispatchers.Main`, the main
 * queue, which only runs while the main run loop turns, so the tests turn it with [runMainLoopUntil].
 */
@OptIn(ExperimentalNativeApi::class, NativeRuntimeApi::class)
class AnchorContainerTest {
  private data object Loaded : Signal

  private data object Ping : Signal

  /** A source the anchor observes from a `connect` handler, like a repository that outlives the screen. */
  private val repository = MutableSharedFlow<Int>(extraBufferCapacity = 16)

  private fun RememberAnchorScope.observing(
    source: Flow<Int>,
    init: (suspend Anchor<EmptyEffect, TestState, Nothing>.() -> Unit)? = null,
  ): Anchor<EmptyEffect, TestState, Nothing> =
    create(
      effectScope = { EmptyEffect },
      initialState = { TestState(value = 0) },
      init = init,
      subscriptions = {
        connect<Created> { source.anchor { value -> reduce { copy(value = value) } } }
      },
    )

  @Test
  fun `a held container keeps one anchor running`() {
    val container = createAnchor({ it.observing(repository) })
    try {
      val anchor = container.anchor
      assertTrue(runMainLoopUntil { repository.subscriptionCount.value == 1 }, "the subscription never attached")
      repository.tryEmit(1)
      assertTrue(runMainLoopUntil { container.state.value == 1 })

      // Holding the container is all it takes: a collection leaves the anchor running.
      GC.collect()

      assertSame(anchor, container.anchor)
      repository.tryEmit(2)
      assertTrue(runMainLoopUntil { container.state.value == 2 }, "the held anchor stopped observing its source")
      assertEquals(1, repository.subscriptionCount.value)
    } finally {
      container.clear()
    }
  }

  @Test
  fun `two containers own independent anchors`() {
    val otherRepository = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val first = createAnchor({ it.observing(repository) })
    val second = createAnchor({ it.observing(otherRepository) })
    try {
      assertNotSame(first.anchor, second.anchor)

      first.anchor.reduce { copy(value = 1) }
      assertEquals(1, first.state.value)
      assertEquals(0, second.state.value)

      assertTrue(runMainLoopUntil { otherRepository.subscriptionCount.value == 1 })
      second.clear()
      assertTrue(runMainLoopUntil { otherRepository.subscriptionCount.value == 0 })

      // Clearing one container leaves the other running.
      assertTrue(runMainLoopUntil { repository.subscriptionCount.value == 1 })
      repository.tryEmit(5)
      assertTrue(runMainLoopUntil { first.state.value == 5 })
    } finally {
      first.clear()
      second.clear()
    }
  }

  @Test
  fun `clear cancels the anchor's init and subscriptions`() {
    val initStarted = CompletableDeferred<Unit>()
    val initCancelled = CompletableDeferred<Unit>()
    val container =
      createAnchor({
        it.observing(repository) {
          initStarted.complete(Unit)
          try {
            awaitCancellation()
          } finally {
            initCancelled.complete(Unit)
          }
        }
      })
    assertTrue(runMainLoopUntil { initStarted.isCompleted && repository.subscriptionCount.value == 1 })

    container.clear()

    assertTrue(runMainLoopUntil { initCancelled.isCompleted }, "init was not cancelled")
    assertTrue(
      runMainLoopUntil { repository.subscriptionCount.value == 0 },
      "the subscription still observes its source",
    )
    repository.tryEmit(1)
    runMainLoopFor(100.milliseconds)
    assertEquals(0, container.state.value)
  }

  @Test
  fun `clear stops the container's collectors`() {
    val container = createAnchor({ it.observing(emptyFlow()) })
    val viewState = (container.anchor as AnchorRuntime<*, *, *>)._viewState
    val states = mutableListOf<Int>()
    val signals = mutableListOf<Signal>()
    container.collectState { states += it.value }
    container.collectSignals { signals += it.provide() }
    assertTrue(runMainLoopUntil { states == listOf(0) })

    container.anchor.reduce { copy(value = 1) }
    runBlocking { container.anchor.post { Ping } }
    assertTrue(runMainLoopUntil { states == listOf(0, 1) && signals == listOf<Signal>(Ping) })
    assertEquals(1, viewState.subscriptionCount.value)

    container.clear()

    assertTrue(runMainLoopUntil { viewState.subscriptionCount.value == 0 }, "a state collector outlived clear()")
    container.anchor.reduce { copy(value = 2) }
    runBlocking { container.anchor.post { Ping } }
    runMainLoopFor(100.milliseconds)
    assertEquals(listOf(0, 1), states)
    assertEquals(listOf<Signal>(Ping), signals)
  }

  @Test
  fun `collectSignals receives a signal posted from init once`() {
    val initDone = CompletableDeferred<Unit>()
    val container =
      createAnchor({
        it.observing(emptyFlow()) {
          post { Loaded }
          initDone.complete(Unit)
        }
      })
    try {
      assertTrue(runMainLoopUntil { initDone.isCompleted })

      val first = mutableListOf<Signal>()
      container.collectSignals { first += it.provide() }
      assertTrue(runMainLoopUntil { first == listOf<Signal>(Loaded) }, "the signal posted from init was dropped")

      val second = mutableListOf<Signal>()
      container.collectSignals { second += it.provide() }
      runMainLoopFor(100.milliseconds)
      assertEquals(emptyList(), second, "a delivered signal was replayed")
    } finally {
      container.clear()
    }
  }

  @Test
  fun `collecting from a cleared container never calls back`() {
    val container = createAnchor({ it.observing(emptyFlow()) })
    container.clear()

    val states = mutableListOf<Int>()
    val collector = container.collectState { states += it.value }
    container.anchor.reduce { copy(value = 1) }
    runMainLoopFor(100.milliseconds)

    assertEquals(emptyList(), states)
    collector.cancel()
    container.clear()
  }

  @Test
  fun `a cleared container leaves nothing reachable from the sources it observed`() {
    val runtime = observeThenDrop(repository, clear = true)

    assertTrue(
      runMainLoopUntil {
        GC.collect()
        runtime.value == null
      },
      "the cleared anchor is still reachable",
    )
  }

  @Test
  fun `an uncleared container stays reachable from the sources it observed`() {
    val runtime = observeThenDrop(repository, clear = false)

    runMainLoopFor(100.milliseconds)
    GC.collect()

    // The leak `clear()` exists for: the source still runs the anchor's subscription.
    assertNotNull(runtime.value)
    assertEquals(1, repository.subscriptionCount.value)
    repository.tryEmit(3)
    assertTrue(runMainLoopUntil { (runtime.value as? Anchor<*, *, *>)?.state == TestState(value = 3) })
  }

  /** Creates a container observing [source], optionally clears it, and returns only a weak reference to its anchor. */
  private fun observeThenDrop(
    source: MutableSharedFlow<Int>,
    clear: Boolean,
  ): WeakReference<Any> {
    val container = createAnchor({ it.observing(source) })
    assertTrue(runMainLoopUntil { source.subscriptionCount.value == 1 })
    val anchor = WeakReference<Any>(container.anchor)
    if (clear) {
      container.clear()
      assertTrue(runMainLoopUntil { source.subscriptionCount.value == 0 })
    }
    return anchor
  }

  /** Turns the main run loop, running what `Dispatchers.Main` queued, until [condition] holds or [timeout] passes. */
  private fun runMainLoopUntil(
    timeout: Duration = 5.seconds,
    condition: () -> Boolean,
  ): Boolean {
    val deadline = TimeSource.Monotonic.markNow() + timeout
    while (!condition()) {
      if (deadline.hasPassedNow()) return false
      NSRunLoop.mainRunLoop.runMode(NSDefaultRunLoopMode, NSDate.dateWithTimeIntervalSinceNow(0.01))
    }
    return true
  }

  /** Turns the main run loop for [duration], so everything `Dispatchers.Main` queued has run. */
  private fun runMainLoopFor(
    duration: Duration,
  ) {
    runMainLoopUntil(duration) { false }
  }
}
