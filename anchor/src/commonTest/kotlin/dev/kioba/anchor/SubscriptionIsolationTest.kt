package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private sealed interface LoadEvent : Event {
  data class Load(val fail: Boolean) : LoadEvent

  data object Probe : LoadEvent
}

private class LoadFailure(message: String) : RuntimeException(message)

/**
 * Pins which errors end a `connect()` listener and which are routed per event
 * while the listener keeps running.
 *
 * An error inside an `.anchor {}` action is routed to `onDomainError` or
 * `defect` for that event only. An error anywhere else in the chain (an
 * operator before or after `.anchor {}`, or a `flatMapLatest` inner flow) is
 * routed once and ends the listener for good. So are unhandled errors,
 * cancellation exceptions that do not come from the scope, and an error
 * handler that throws. Sibling listeners keep running in every case.
 *
 * `anchorErrors()` at the end of an inner flow keeps the listener alive; see
 * `AnchorErrorsTest`.
 */
class SubscriptionIsolationTest {

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
  fun `a defect thrown inside an anchor action is routed and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e.message } }) {
          connect<LoadEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) throw LoadFailure("action failed")
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        anchor.emit { LoadEvent.Load(fail = false) }

        assertEquals(listOf(false), loads.awaitSize(1))
        assertEquals(listOf<String?>("action failed"), defects.value)
        supervisor.awaitLiveListeners(1)
      }
    }

  @Test
  fun `a domain error raised inside an anchor action is routed and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val errors = MutableStateFlow<List<TestError>>(emptyList())
      val anchor =
        createAnchor(onDomainError = { error -> errors.update { it + error } }) {
          connect<LoadEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) raise(TestError.NotFound)
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        anchor.emit { LoadEvent.Load(fail = false) }

        assertEquals(listOf(false), loads.awaitSize(1))
        assertEquals(listOf<TestError>(TestError.NotFound), errors.value)
        supervisor.awaitLiveListeners(1)
      }
    }

  @Test
  fun `a handled error thrown upstream of anchor ends the listener`(): Unit =
    runBlocking {
      val received = MutableStateFlow<List<Event>>(emptyList())
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val probes = MutableStateFlow<List<Event>>(emptyList())
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e.message } }) {
          connect<Event> { events ->
            events
              .onEach { event -> received.update { it + event } }
              .map { event ->
                if (event is LoadEvent.Load && event.fail) throw LoadFailure("upstream failed")
                event
              }
              .anchor { event -> if (event is LoadEvent.Load) loads.update { it + event.fail } }
          }
          connect<LoadEvent.Probe> { events -> events.onEach { event -> probes.update { it + event } } }
        }

      anchor.withListeners(listeners = 2) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        assertEquals(listOf<String?>("upstream failed"), defects.awaitSize(1))
        supervisor.awaitLiveListeners(1)

        anchor.emit { LoadEvent.Load(fail = false) }
        anchor.emit { LoadEvent.Probe }
        probes.awaitSize(1)

        // Routed once, then gone: the next Load is never seen, and the
        // listener does not come back (no second Created).
        assertEquals(listOf(Created, LoadEvent.Load(fail = true)), received.value)
        assertEquals(emptyList(), loads.value)
        assertEquals(1, anchor._emitter.subscriptionCount.value)
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `a handled error from a flatMapLatest inner flow ends the listener`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Int>>(emptyList())
      val probes = MutableStateFlow<List<Event>>(emptyList())
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e.message } }) {
          connect<LoadEvent.Load> { events ->
            events
              .flatMapLatest { event ->
                flow {
                  if (event.fail) throw LoadFailure("fetch failed")
                  emit(1)
                }
              }.anchor { value -> loads.update { it + value } }
          }
          connect<LoadEvent.Probe> { events -> events.onEach { event -> probes.update { it + event } } }
        }

      anchor.withListeners(listeners = 2) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        assertEquals(listOf<String?>("fetch failed"), defects.awaitSize(1))
        supervisor.awaitLiveListeners(1)

        anchor.emit { LoadEvent.Load(fail = false) }
        anchor.emit { LoadEvent.Probe }
        probes.awaitSize(1)

        assertEquals(emptyList(), loads.value)
        assertEquals(listOf<String?>("fetch failed"), defects.value)
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `catching inside the flatMapLatest inner flow keeps the listener alive`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Int>>(emptyList())
      val anchor =
        createAnchor(defect = { throw AssertionError("defect must not be reached") }) {
          connect<LoadEvent.Load> { events ->
            events
              .flatMapLatest { event ->
                flow {
                  if (event.fail) throw LoadFailure("fetch failed")
                  emit(1)
                }.catch { emit(-1) }
              }.anchor { value -> loads.update { it + value } }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        anchor.emit { LoadEvent.Load(fail = false) }

        assertEquals(listOf(-1, 1), loads.awaitSize(2))
        supervisor.awaitLiveListeners(1)
      }
    }

  @Test
  fun `a handled failure in one listener does not affect its sibling`(): Unit =
    runBlocking {
      val probes = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(defect = {}) {
          connect<LoadEvent.Load> { events ->
            events.map<LoadEvent.Load, Unit> { throw LoadFailure("upstream failed") }
          }
          connect<LoadEvent.Probe> { events -> events.onEach { event -> probes.update { it + event } } }
        }

      anchor.withListeners(listeners = 2) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(1)

        anchor.emit { LoadEvent.Probe }
        anchor.emit { LoadEvent.Probe }

        assertEquals(listOf<Event>(LoadEvent.Probe, LoadEvent.Probe), probes.awaitSize(2))
      }
    }

  @Test
  fun `an unhandled defect inside an anchor action ends only its listener`(): Unit =
    runBlocking {
      val probes = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor {
          connect<LoadEvent.Load> { events -> events.anchor { throw LoadFailure("action failed") } }
          connect<LoadEvent.Probe> { events -> events.onEach { event -> probes.update { it + event } } }
        }

      anchor.withListeners(listeners = 2) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(1)

        anchor.emit { LoadEvent.Probe }
        assertEquals(listOf<Event>(LoadEvent.Probe), probes.awaitSize(1))
        assertTrue(supervisor.isActive)
      }
    }

  @Test
  fun `a domain error with only defect configured is routed and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<LoadEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) raise(TestError.NotFound)
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        anchor.emit { LoadEvent.Load(fail = false) }

        // With no onDomainError, the error escalates to defect as orDie
        // would, and is handled for this event only.
        assertEquals(listOf(false), loads.awaitSize(1))
        assertEquals(TestError.NotFound, assertIs<DomainDefectException>(defects.value.single()).error)
        supervisor.awaitLiveListeners(1)
      }
    }

  @Test
  fun `a timeout inside an anchor action is routed to defect and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<LoadEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) withTimeout(1) { awaitCancellation() }
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        anchor.emit { LoadEvent.Load(fail = false) }

        // The listener is still active when the timeout fires, so the
        // cancellation is a failure of this event, not the end of the chain.
        assertEquals(listOf(false), loads.awaitSize(1))
        assertIs<TimeoutCancellationException>(defects.value.single())
        supervisor.awaitLiveListeners(1)
      }
    }

  // Pins current behavior that plans/030 is expected to change (the handler
  // runs twice). Update this test there; nothing else depends on it.
  @Test
  fun `a defect handler that throws ends the listener`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            throw IllegalStateException("defect handler failed")
          },
        ) {
          connect<LoadEvent.Load> { events -> events.anchor { throw LoadFailure("action failed") } }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { LoadEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(0)

        // The chain-level catch hands the handler's own failure back to it.
        assertEquals(listOf<String?>("action failed", "defect handler failed"), defects.value)
      }
    }

  @Test
  fun `cancelling the subscribing scope stops every listener without reaching defect`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<LoadEvent.Load> { events -> events.anchor { awaitCancellation() } }
          connect<LoadEvent.Probe> { events -> events.onEach {} }
        }

      coroutineScope {
        val supervisor = CompletableDeferred<Job>()
        val subscriber = launch { supervisor.complete(with(anchor) { subscribe() }) }
        val job = supervisor.await()
        withTimeout(2_000) {
          while (anchor._emitter.subscriptionCount.value < 2) yield()
        }
        anchor.emit { LoadEvent.Load(fail = false) }
        yield()

        subscriber.cancelAndJoin()

        assertTrue(job.isCancelled)
        assertEquals(0, anchor._emitter.subscriptionCount.value)
        assertEquals(emptyList(), defects.value)
      }
    }
}
