package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private sealed interface FetchEvent : Event {
  data class Fetch(val fail: Boolean) : FetchEvent

  data object Hang : FetchEvent

  data object Probe : FetchEvent
}

private class FetchFailure(message: String) : RuntimeException(message)

@OptIn(ExperimentalCoroutinesApi::class)
class AnchorErrorsTest {

  private fun createAnchor(
    onDomainError: (suspend ErrorScope<EmptyEffect, TestState>.(TestError) -> Unit)? = null,
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
    subscriptions: suspend SubscriptionsScope<EmptyEffect, TestState, TestError>.() -> Unit,
  ): AnchorRuntime<EmptyEffect, TestState, TestError> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      subscriptions = subscriptions,
      onDomainError = onDomainError,
      defect = defect,
    )

  /**
   * Subscribes, waits until [listeners] handlers are attached, and runs
   * [block] with the job that parents every listener. Always tears the
   * listeners down, so none outlives the test.
   */
  private suspend fun AnchorRuntime<EmptyEffect, TestState, TestError>.withListeners(
    listeners: Int,
    block: suspend (supervisor: Job) -> Unit,
  ): Unit =
    coroutineScope {
      val supervisor = CompletableDeferred<Job>()
      val subscriber = launch { supervisor.complete(subscribe()) }
      try {
        val job = supervisor.await()
        withTimeout(2_000) {
          while (_emitter.subscriptionCount.value < listeners) yield()
        }
        block(job)
      } finally {
        subscriber.cancelAndJoin()
      }
    }

  private suspend fun Job.awaitLiveListeners(count: Int) {
    withTimeout(2_000) {
      while (children.count() != count) yield()
    }
  }

  private suspend fun <T> MutableStateFlow<List<T>>.awaitSize(size: Int): List<T> =
    withTimeout(2_000) { first { it.size >= size } }

  @Test
  fun `a defect from an inner flow is routed and later events still work`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Int>>(emptyList())
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e.message } }) {
          connect<FetchEvent.Fetch> { events ->
            events
              .flatMapLatest { event ->
                flow {
                  if (event.fail) throw FetchFailure("fetch failed")
                  emit(1)
                }.anchorErrors()
              }.anchor { value -> loads.update { it + value } }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { FetchEvent.Fetch(fail = true) }
        assertEquals(listOf<String?>("fetch failed"), defects.awaitSize(1))

        anchor.emit { FetchEvent.Fetch(fail = false) }
        assertEquals(listOf(1), loads.awaitSize(1))

        anchor.emit { FetchEvent.Fetch(fail = true) }
        anchor.emit { FetchEvent.Fetch(fail = false) }
        assertEquals(listOf(1, 1), loads.awaitSize(2))
        assertEquals(listOf<String?>("fetch failed", "fetch failed"), defects.value)
        supervisor.awaitLiveListeners(1)
      }
    }

  @Test
  fun `a domain error from an inner flow is routed to onDomainError`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Int>>(emptyList())
      val errors = MutableStateFlow<List<TestError>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(
          onDomainError = { error -> errors.update { it + error } },
          defect = { e -> defects.update { it + e } },
        ) {
          connect<FetchEvent.Fetch> { events ->
            events
              .flatMapLatest { event ->
                flow {
                  if (event.fail) anchor.raise(TestError.NotFound)
                  emit(1)
                }.anchorErrors()
              }.anchor { value -> loads.update { it + value } }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { FetchEvent.Fetch(fail = true) }
        assertEquals(listOf<TestError>(TestError.NotFound), errors.awaitSize(1))

        anchor.emit { FetchEvent.Fetch(fail = false) }
        assertEquals(listOf(1), loads.awaitSize(1))
        assertEquals(emptyList(), defects.value)
        supervisor.awaitLiveListeners(1)
      }
    }

  @Test
  fun `each failing inner run is routed exactly once`(): Unit =
    runBlocking {
      val runs = MutableStateFlow(0)
      val probes = MutableStateFlow<List<Event>>(emptyList())
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e.message } }) {
          connect<FetchEvent.Fetch> { events ->
            // Fails on every event, the way a fetch does while offline.
            events.flatMapLatest {
              flow<Int> {
                runs.update { it + 1 }
                throw FetchFailure("offline")
              }.anchorErrors()
            }
          }
          connect<FetchEvent.Probe> { events -> events.onEach { event -> probes.update { it + event } } }
        }

      anchor.withListeners(listeners = 2) { supervisor ->
        repeat(5) {
          anchor.emit { FetchEvent.Fetch(fail = true) }
          defects.awaitSize(it + 1)
        }

        // No retry: nothing runs again until the next event.
        anchor.emit { FetchEvent.Probe }
        probes.awaitSize(1)
        assertEquals(5, runs.value)
        assertEquals(List<String?>(5) { "offline" }, defects.value)
        supervisor.awaitLiveListeners(2)
      }
    }

  @Test
  fun `an inner flow that flatMapLatest cancels is not routed`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Int>>(emptyList())
      val started = CompletableDeferred<Unit>()
      val routed = MutableStateFlow<List<Any>>(emptyList())
      val anchor =
        createAnchor(
          onDomainError = { error -> routed.update { it + error } },
          defect = { e -> routed.update { it + e } },
        ) {
          connect<FetchEvent> { events ->
            events
              .flatMapLatest { event ->
                flow {
                  if (event is FetchEvent.Hang) {
                    started.complete(Unit)
                    awaitCancellation()
                  }
                  emit(1)
                }.anchorErrors()
              }.anchor { value -> loads.update { it + value } }
          }
        }

      anchor.withListeners(listeners = 1) {
        anchor.emit { FetchEvent.Hang }
        withTimeout(2_000) { started.await() }

        // The next event cancels the hanging inner flow.
        anchor.emit { FetchEvent.Fetch(fail = false) }

        assertEquals(listOf(1), loads.awaitSize(1))
        assertEquals(emptyList(), routed.value)
      }
    }

  @Test
  fun `cancelling the subscribing scope is not routed`(): Unit =
    runBlocking {
      val started = CompletableDeferred<Unit>()
      val routed = MutableStateFlow<List<Any>>(emptyList())
      val anchor =
        createAnchor(
          onDomainError = { error -> routed.update { it + error } },
          defect = { e -> routed.update { it + e } },
        ) {
          connect<FetchEvent.Hang> { events ->
            events.flatMapLatest {
              flow<Int> {
                started.complete(Unit)
                awaitCancellation()
              }.anchorErrors()
            }
          }
        }

      coroutineScope {
        val supervisor = CompletableDeferred<Job>()
        val subscriber = launch { supervisor.complete(with(anchor) { subscribe() }) }
        val job = supervisor.await()
        withTimeout(2_000) {
          while (anchor._emitter.subscriptionCount.value < 1) yield()
        }
        anchor.emit { FetchEvent.Hang }
        withTimeout(2_000) { started.await() }

        subscriber.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(emptyList(), routed.value)
      }
    }

  @Test
  fun `without a matching handler the error ends only its listener`(): Unit =
    runBlocking {
      val probes = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor {
          connect<FetchEvent.Fetch> { events ->
            events.flatMapLatest {
              flow<Int> { throw FetchFailure("offline") }.anchorErrors()
            }
          }
          connect<FetchEvent.Probe> { events -> events.onEach { event -> probes.update { it + event } } }
        }

      anchor.withListeners(listeners = 2) { supervisor ->
        anchor.emit { FetchEvent.Fetch(fail = true) }
        supervisor.awaitLiveListeners(1)

        anchor.emit { FetchEvent.Probe }
        assertEquals(listOf<Event>(FetchEvent.Probe), probes.awaitSize(1))
      }
    }
}
