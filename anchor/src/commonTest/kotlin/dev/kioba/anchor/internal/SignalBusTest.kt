package dev.kioba.anchor.internal

import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.fail

private sealed interface BusSignal : Signal {
  data class Toast(
    val n: Int,
  ) : BusSignal

  data class Nav(
    val n: Int,
  ) : BusSignal
}

private val isToast: (Signal) -> Boolean = { it is BusSignal.Toast }
private val isNav: (Signal) -> Boolean = { it is BusSignal.Nav }

private suspend fun SignalBus.post(
  signal: Signal,
): Unit =
  post(SignalProvider { signal })

/** The first signal [this] delivers, or null if none arrives within [timeoutMillis]. */
private suspend fun Flow<SignalProvider>.firstOrNull(
  timeoutMillis: Long,
): Signal? =
  withTimeoutOrNull(timeoutMillis) { first().provide() }

/** Collects [this] into a channel, attached before this returns. */
private fun CoroutineScope.collectInto(
  flow: Flow<SignalProvider>,
  received: Channel<Signal>,
): Job =
  launch(start = CoroutineStart.UNDISPATCHED) {
    flow.collect { received.send(it.provide()) }
  }

/** Receives [count] signals from [this], failing with [message] if they do not all arrive in time. */
private suspend fun Channel<Signal>.receive(
  count: Int,
  message: String,
): List<Signal> =
  withTimeoutOrNull(5_000) { List(count) { receive() } } ?: fail(message)

class SignalBusTest {
  @Test
  fun `raw late collector receives held signal once and it is not replayed`(): Unit =
    runBlocking {
      val bus = SignalBus()
      bus.post(BusSignal.Toast(1))

      assertEquals(BusSignal.Toast(1), bus.signals.firstOrNull(1_000))
      assertNull(bus.signals.firstOrNull(100), "a delivered signal was replayed")
    }

  @Test
  fun `typed collectors each receive their own held signal`(): Unit =
    runBlocking {
      val bus = SignalBus()
      bus.post(BusSignal.Nav(1))
      bus.post(BusSignal.Toast(1))

      // The Toast collector attaches first; it must leave the held Nav for the Nav collector.
      assertEquals(BusSignal.Toast(1), bus.signalsMatching(isToast).firstOrNull(1_000))
      assertEquals(BusSignal.Nav(1), bus.signalsMatching(isNav).firstOrNull(1_000))
      assertNull(bus.signals.firstOrNull(100), "a delivered signal was still held")
    }

  @Test
  fun `unmatched typed signal stays held while another typed collector is live`(): Unit =
    runBlocking {
      val bus = SignalBus()
      val toasts = Channel<Signal>(Channel.UNLIMITED)
      val toastCollector = collectInto(bus.signalsMatching(isToast), toasts)

      bus.post(BusSignal.Nav(1))
      bus.post(BusSignal.Toast(1))

      assertEquals(listOf(BusSignal.Toast(1)), toasts.receive(1, "the live Toast collector missed its signal"))
      assertEquals(BusSignal.Nav(1), bus.signalsMatching(isNav).firstOrNull(1_000))
      toastCollector.cancelAndJoin()
      assertNull(toasts.tryReceive().getOrNull(), "the Toast collector received a Nav")
    }

  @Test
  fun `live signal fans out to raw and typed collectors`(): Unit =
    runBlocking {
      val bus = SignalBus()
      val raw1 = Channel<Signal>(Channel.UNLIMITED)
      val raw2 = Channel<Signal>(Channel.UNLIMITED)
      val toasts1 = Channel<Signal>(Channel.UNLIMITED)
      val toasts2 = Channel<Signal>(Channel.UNLIMITED)
      val collectors =
        listOf(
          collectInto(bus.signals, raw1),
          collectInto(bus.signals, raw2),
          collectInto(bus.signalsMatching(isToast), toasts1),
          collectInto(bus.signalsMatching(isToast), toasts2),
        )

      bus.post(BusSignal.Toast(1))

      for (channel in listOf(raw1, raw2, toasts1, toasts2)) {
        assertEquals(listOf(BusSignal.Toast(1)), channel.receive(1, "a live collector missed the signal"))
      }
      collectors.forEach { it.cancelAndJoin() }
      // Delivered live, so it was never held for a later collector.
      assertNull(bus.signals.firstOrNull(100), "a live signal was also held")
    }

  @Test
  fun `held then live signals keep order`(): Unit =
    runBlocking {
      for (typed in listOf(false, true)) {
        val bus = SignalBus()
        val stream = if (typed) bus.signalsMatching(isToast) else bus.signals
        bus.post(BusSignal.Toast(1))
        bus.post(BusSignal.Toast(2))

        // Park the collector on the first held signal, with the second still being handed over, then post live.
        val parked = CompletableDeferred<Unit>()
        val received = Channel<Signal>(Channel.UNLIMITED)
        val collector =
          launch(start = CoroutineStart.UNDISPATCHED) {
            stream.collect {
              received.send(it.provide())
              parked.await()
            }
          }
        bus.post(BusSignal.Toast(3))
        parked.complete(Unit)

        assertEquals(
          listOf(BusSignal.Toast(1), BusSignal.Toast(2), BusSignal.Toast(3)),
          received.receive(3, "typed=$typed: a signal went missing"),
          "typed=$typed",
        )
        collector.cancelAndJoin()
      }
    }

  @Test
  fun `held buffer is bounded and drops oldest`(): Unit =
    runBlocking {
      val bus = SignalBus()
      val capacity = SignalBus.HELD_CAPACITY
      repeat(capacity + 5) { bus.post(BusSignal.Toast(it)) }

      val drained =
        withTimeoutOrNull(1_000) {
          bus.signals
            .take(capacity)
            .toList()
            .map { it.provide() }
        }
      assertEquals((5 until capacity + 5).map { BusSignal.Toast(it) }, drained)
      assertNull(bus.signals.firstOrNull(100), "more than HELD_CAPACITY signals were held")
    }

  @Test
  fun `concurrent posts vs subscribe lose nothing and duplicate nothing`(): Unit =
    runBlocking(Dispatchers.Default) {
      val posters = 200
      for (typed in listOf(false, true)) {
        repeat(200) { round ->
          // Room to hold every post, so only a race (not the bound) can lose one.
          val bus = SignalBus(capacity = posters)
          val received = Channel<Signal>(Channel.UNLIMITED)
          val postJobs = List(posters) { n -> launch { bus.post(BusSignal.Toast(n)) } }
          val collector =
            launch {
              val stream = if (typed) bus.signalsMatching(isToast) else bus.signals
              stream.collect { received.send(it.provide()) }
            }
          postJobs.joinAll()

          val got = received.receive(posters, "typed=$typed round=$round: a signal was lost")
          assertEquals(
            (0 until posters).map { BusSignal.Toast(it) }.toSet(),
            got.toSet(),
            "typed=$typed round=$round: a signal was duplicated",
          )
          yield()
          assertNull(received.tryReceive().getOrNull(), "typed=$typed round=$round: a signal was duplicated")
          collector.cancelAndJoin()
        }
      }
    }

  @Test
  fun `a collector attaching behind queued posts does not deadlock`(): Unit =
    runBlocking {
      val capacity = SignalBus.HELD_CAPACITY
      val bus = SignalBus()
      val scope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
      try {
        // A collector that takes one signal and never another fills the live buffer.
        val stuck = scope.launch(start = CoroutineStart.UNDISPATCHED) { bus.signals.collect { awaitCancellation() } }
        repeat(capacity + 1) { bus.post(BusSignal.Nav(it)) }

        // One post waits for buffer space; more posts queue up behind it.
        scope.launch { bus.post(BusSignal.Toast(0)) }
        yield()
        repeat(capacity + 1) { scope.launch { bus.post(BusSignal.Toast(it + 1)) } }
        yield()

        // A new collector attaches; the stuck one then leaves and frees the buffer.
        val received = Channel<Signal>(Channel.UNLIMITED)
        scope.launch { bus.signals.collect { received.send(it.provide()) } }
        yield()
        stuck.cancel()

        val got = received.receive(capacity + 2, "posting deadlocked against the attaching collector")
        assertEquals((0..capacity + 1).map { BusSignal.Toast(it) }, got.sortedBy { (it as BusSignal.Toast).n })
      } finally {
        scope.coroutineContext[Job]?.cancelAndJoin()
      }
    }
}
