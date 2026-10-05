package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

private sealed interface BusEvent : Event {
  data object Ping : BusEvent

  data object Pong : BusEvent

  data object Other : BusEvent

  data class Seq(val value: Int) : BusEvent
}

class EventBusReentrancyTest {

  private fun createAnchor(
    onDomainError: (suspend ErrorScope<EmptyEffect, TestState>.(TestError) -> Unit)? = null,
    subscriptions: suspend SubscriptionsScope<EmptyEffect, TestState, TestError>.() -> Unit,
  ): AnchorRuntime<EmptyEffect, TestState, TestError> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      subscriptions = subscriptions,
      onDomainError = onDomainError,
    )

  /**
   * Subscribes [handlers] connect() flows, runs [block], and always tears the
   * subscriptions down — even when [block] times out on a wedged bus — so no
   * stuck coroutine outlives the test (a leak would abort on Kotlin/Native).
   */
  private suspend fun AnchorRuntime<EmptyEffect, TestState, TestError>.withSubscriptions(
    handlers: Int,
    block: suspend () -> Unit,
  ): Unit =
    coroutineScope {
      val subscriber = launch { subscribe() }
      try {
        withTimeout(2_000) {
          while (_emitter.subscriptionCount.value < handlers) yield()
        }
        block()
      } finally {
        // subscribe()'s supervisor is a child of the subscriber job, so this
        // cancels the supervisor and every handler collector under it.
        subscriber.cancelAndJoin()
      }
    }

  @Test
  fun `an action run by a connect handler can emit without blocking later emits`(): Unit =
    runBlocking {
      var pongs = 0
      val anchor =
        createAnchor {
          connect<BusEvent.Ping> { events -> events.anchor { emit { BusEvent.Pong } } }
          connect<BusEvent.Pong> { events -> events.onEach { pongs += 1 } }
        }

      anchor.withSubscriptions(handlers = 2) {
        withTimeout(2_000) { anchor._emitter.emit(BusEvent.Ping) }

        // Before the fix, the Ping handler's emit {} waits on the Ping handler
        // itself, so every later emission on the bus suspends forever.
        withTimeout(2_000) { anchor.emit { BusEvent.Other } }
        withTimeout(2_000) { anchor._emitter.emit(BusEvent.Ping) }

        withTimeout(2_000) {
          while (pongs < 2) yield()
        }
        assertEquals(2, pongs)
      }
    }

  @Test
  fun `onDomainError can emit for an error raised in a handler action`(): Unit =
    runBlocking {
      var pongs = 0
      val anchor =
        createAnchor(
          onDomainError = { emit { BusEvent.Pong } },
        ) {
          connect<BusEvent.Ping> { events -> events.anchor { raise(TestError.NotFound) } }
          connect<BusEvent.Pong> { events -> events.onEach { pongs += 1 } }
        }

      anchor.withSubscriptions(handlers = 2) {
        withTimeout(2_000) { anchor._emitter.emit(BusEvent.Ping) }

        // onDomainError runs inline in the Ping handler's collector, so its
        // emit {} wedged the bus the same way an action's emit {} did.
        withTimeout(2_000) { anchor.emit { BusEvent.Other } }

        withTimeout(2_000) {
          while (pongs < 1) yield()
        }
        assertEquals(1, pongs)
      }
    }

  @Test
  fun `each handler receives events in emission order`(): Unit =
    runBlocking {
      val received = mutableListOf<Int>()
      val anchor =
        createAnchor {
          connect<BusEvent.Seq> { events -> events.onEach { received += it.value } }
        }

      anchor.withSubscriptions(handlers = 1) {
        withTimeout(2_000) {
          repeat(100) { anchor.emit { BusEvent.Seq(it) } }
          while (received.size < 100) yield()
        }
        assertEquals((0 until 100).toList(), received)
      }
    }

  @Test
  fun `every handler receives every event`(): Unit =
    runBlocking {
      val first = mutableListOf<Int>()
      val second = mutableListOf<Int>()
      val anchor =
        createAnchor {
          connect<BusEvent.Seq> { events -> events.onEach { first += it.value } }
          connect<BusEvent.Seq> { events -> events.onEach { second += it.value } }
        }

      anchor.withSubscriptions(handlers = 2) {
        withTimeout(2_000) {
          repeat(10) { anchor.emit { BusEvent.Seq(it) } }
          while (first.size < 10 || second.size < 10) yield()
        }
        assertEquals((0 until 10).toList(), first)
        assertEquals((0 until 10).toList(), second)
      }
    }

  @Test
  fun `each handler receives Created once`(): Unit =
    runBlocking {
      val first = mutableListOf<Event>()
      val second = mutableListOf<Event>()
      val anchor =
        createAnchor {
          connect<Event> { events -> events.onEach { first += it } }
          connect<Event> { events -> events.onEach { second += it } }
        }

      anchor.withSubscriptions(handlers = 2) {
        withTimeout(2_000) {
          anchor.emit { BusEvent.Seq(0) }
          anchor.emit { BusEvent.Seq(1) }
          while (first.size < 3 || second.size < 3) yield()
        }
        val expected = listOf(Created, BusEvent.Seq(0), BusEvent.Seq(1))
        assertEquals(expected, first)
        assertEquals(expected, second)
      }
    }
}
