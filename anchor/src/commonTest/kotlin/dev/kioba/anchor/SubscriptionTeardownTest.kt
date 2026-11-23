package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.EVENT_BUFFER_CAPACITY
import dev.kioba.anchor.internal.INBOX_CAPACITY
import dev.kioba.anchor.internal.Inbox
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

private data class Tick(val n: Int) : Event

/**
 * What happens to the event channel and the handlers' inboxes once nothing
 * can read them any more: a handler has ended, or subscriptions were torn down.
 */
class SubscriptionTeardownTest {

  private fun createAnchor(
    subscriptions: (suspend SubscriptionsScope<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
  ) =
    AnchorRuntime<EmptyEffect, TestState, TestError>(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      subscriptions = subscriptions,
    )

  @Test
  fun `a handler that dies with a full inbox does not stop delivery to the others`(): Unit =
    runBlocking {
      val gate = CompletableDeferred<Unit>()
      val received = mutableListOf<Int>()
      val anchor =
        createAnchor {
          connect<Tick> { events ->
            events.onEach { tick ->
              if (tick.n == 0) {
                gate.await()
                throw IllegalStateException("handler died")
              }
            }
          }
          connect<Tick> { events -> events.onEach { received += it.n } }
        }

      coroutineScope {
        val subscriber = launch { anchor.subscribe() }
        val total = INBOX_CAPACITY + EVENT_BUFFER_CAPACITY + 10
        val emitter = launch { repeat(total) { anchor.emit { Tick(it) } } }

        withTimeout(2_000) {
          while (received.size < INBOX_CAPACITY + 1) yield()
        }
        gate.complete(Unit)

        withTimeout(2_000) {
          while (received.size < total) yield()
        }
        assertEquals(List(total) { it }, received)

        emitter.cancelAndJoin()
        subscriber.cancelAndJoin()
      }
    }

  @Test
  fun `emit after subscriptions are torn down drops instead of suspending`(): Unit =
    runBlocking {
      val anchor = createAnchor { connect<Tick> { events -> events } }

      coroutineScope {
        val subscriber = launch { anchor.subscribe() }
        yield()
        subscriber.cancelAndJoin()

        withTimeout(2_000) {
          repeat(200) { anchor.emit { Tick(it) } }
        }
      }
    }

  @Test
  fun `closing an inbox releases the events it holds`(): Unit =
    runBlocking {
      val inbox = Inbox()
      repeat(10) { inbox.deliver(Tick(it)) }

      inbox.close()
      inbox.deliver(Tick(10))

      assertEquals(emptyList(), withTimeout(2_000) { inbox.input.toList() })
    }
}
