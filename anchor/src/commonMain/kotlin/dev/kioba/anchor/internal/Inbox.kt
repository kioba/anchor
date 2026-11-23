package dev.kioba.anchor.internal

import dev.kioba.anchor.Created
import dev.kioba.anchor.Event
import kotlinx.coroutines.channels.Channel
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
 * finds its inbox full is dropped. So an inbox holds the whole startup backlog
 * (at most [EVENT_BUFFER_CAPACITY] events) plus [Created], with as much again
 * to spare for events emitted before the handler gets going.
 */
private const val INBOX_CAPACITY: Int = 2 * EVENT_BUFFER_CAPACITY

/**
 * One `connect()` handler's own queue of events, created when the handler is
 * declared and starting with [Created]. It exists before the handler runs, so
 * the handler receives every event delivered to it, however late it starts
 * collecting.
 *
 * While the handler is collecting [input], delivery waits for room, so a slow
 * handler holds up `emit {}` instead of losing events. While it is not, an
 * event that finds the inbox full is dropped, so a handler that never reads
 * its input, or has stopped, holds nothing up.
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

  suspend fun deliver(
    event: Event,
  ) {
    if (reading) channel.send(event) else channel.trySend(event)
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
