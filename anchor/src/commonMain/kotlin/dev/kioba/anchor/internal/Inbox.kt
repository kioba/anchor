package dev.kioba.anchor.internal

import dev.kioba.anchor.Created
import dev.kioba.anchor.Event
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlin.concurrent.Volatile

/**
 * Capacity of the channel `emit {}` sends into. The channel holds every event
 * emitted before the handlers start, so `init` can emit at most this many
 * before it waits on handlers that start only once it returns.
 */
internal const val EVENT_BUFFER_CAPACITY: Int = 64

/**
 * Capacity of each handler's inbox. A handler whose events are collected by
 * a coroutine an operator starts (`flatMapLatest`, `buffer`, `combine`) starts
 * reading a moment after delivery starts, and until it does, an event that
 * finds its inbox full is dropped. So an inbox holds exactly the startup
 * backlog the [EVENT_BUFFER_CAPACITY]-event channel can hold, plus [Created].
 * Actions suspended in `emit {}` before startup add their events as soon as
 * the channel drains, so a handler that is slow to start can still lose those.
 * A handler that never reads its events keeps at most this many.
 */
internal const val INBOX_CAPACITY: Int = EVENT_BUFFER_CAPACITY + 1

/**
 * One `connect()` handler's own queue of events, created when the handler is
 * declared and starting with [Created]. It exists before the handler runs, so
 * the handler receives every event delivered to it, however late it starts
 * collecting, as long as the inbox did not fill up first.
 *
 * While the handler is collecting [input], delivery waits for room, so a slow
 * handler holds up delivery to every handler and, once the event channel is
 * full, `emit {}` itself, instead of losing events. While it is not, an event
 * that finds the inbox full is dropped, so a handler that never reads its
 * input holds nothing up. Once the handler has ended, [close] drops
 * everything.
 */
internal class Inbox {
  private val channel = Channel<Event>(INBOX_CAPACITY).apply { trySend(Created) }

  @Volatile
  private var reading: Boolean = false

  val input: Flow<Event> =
    flow {
      reading = true
      try {
        emitAll(channel.receiveAsFlow())
      } finally {
        reading = false
      }
    }

  /**
   * Delivers [event], or drops it if the inbox is closed. A delivery waiting
   * for room when the inbox closes returns instead of waiting forever.
   */
  suspend fun deliver(
    event: Event,
  ) {
    if (reading) {
      try {
        channel.send(event)
      } catch (_: ClosedSendChannelException) {
      }
    } else {
      channel.trySend(event)
    }
  }

  /**
   * Closes the inbox once its handler has ended, and releases the events it
   * still holds. Later deliveries are dropped.
   *
   * Draining is not optional: closing a channel does not resume a send that
   * is already waiting for room. Receiving the buffered events is what frees
   * a delivery stuck on this full inbox, so delivery to the other handlers
   * carries on.
   */
  fun close() {
    channel.close()
    channel.drain()
  }
}

/** Removes every element still buffered in a closed channel, so it can be garbage-collected. */
internal fun Channel<*>.drain() {
  while (tryReceive().isSuccess) {
    continue
  }
}

/**
 * A `connect()` handler: the [flow] its block built on top of [inbox]'s input.
 * The runtime delivers events to [inbox] and collects [flow].
 */
internal class InboxedFlow(
  val inbox: Inbox,
  val flow: Flow<*>,
)
