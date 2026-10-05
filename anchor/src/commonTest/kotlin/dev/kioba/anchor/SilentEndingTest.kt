package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.internal.safeExecute
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.ContainerViewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

private sealed interface SilentEvent : Event {
  data class Load(val fail: Boolean) : SilentEvent

  data object Ping : SilentEvent
}

private class SilentFailure(message: String) : RuntimeException(message)

private class WrappedFailure(message: String, cause: Throwable) : RuntimeException(message, cause)

/**
 * Errors that used to end work without reaching any handler: a cancellation
 * that does not come from cancelling the coroutine (a `withTimeout` expiry, a
 * `Deferred` cancelled elsewhere), a `raise` with no `onDomainError`, a
 * `defect` handler that throws inside a subscription, and `init` ending in
 * either of the first two. Each is checked on the path it breaks: an
 * `.anchor {}` listener, an executed action, or `init`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SilentEndingTest {

  private fun createAnchor(
    init: (suspend Anchor<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
    onDomainError: (suspend ErrorScope<EmptyEffect, TestState>.(TestError) -> Unit)? = null,
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
  ): AnchorRuntime<EmptyEffect, TestState, TestError> =
    AnchorRuntime(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
      init = init,
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

  /**
   * Starts the anchor in a [ContainerViewModel], runs [block] with it, and
   * clears the ViewModel afterwards so no subscription outlives the test.
   */
  private suspend fun AnchorRuntime<EmptyEffect, TestState, TestError>.inViewModel(
    block: suspend (ContainerViewModel<EmptyEffect, TestState, TestError>) -> Unit,
  ) {
    val store = ViewModelStore()
    val owner =
      object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
      }
    val runtime = this
    val provider = ViewModelProvider.create(owner, ContainerViewModelFactory { ContainerViewModel(runtime) })

    @Suppress("UNCHECKED_CAST")
    val viewModel = provider[ContainerViewModel::class] as ContainerViewModel<EmptyEffect, TestState, TestError>
    try {
      block(viewModel)
    } finally {
      store.clear()
    }
  }

  /**
   * Runs [block] behind `safeExecute` the way `ContainerViewModel.execute`
   * does, in a scope whose exception handler records what escapes instead of
   * handing it to the platform (which aborts Kotlin/Native). Returns the
   * finished job and the escaped exception, if any.
   */
  private suspend fun AnchorRuntime<EmptyEffect, TestState, TestError>.executeMirror(
    block: suspend Anchor<EmptyEffect, TestState, TestError>.() -> Unit,
  ): Pair<Job, Throwable?> {
    val escaped = CompletableDeferred<Throwable>()
    val scope =
      CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> escaped.complete(e) },
      )
    try {
      val runtime = this
      val job = scope.launch { safeExecute(runtime, runtime.onDomainError, runtime.defect) { runtime.block() } }
      job.join()
      return job to (if (escaped.isCompleted) escaped.await() else null)
    } finally {
      scope.cancel()
    }
  }

  /**
   * An anchor whose single `Ping` handler sets the state to 1, so a test can
   * tell whether subscriptions still run after `init`.
   */
  private fun pingAnchor(
    init: suspend Anchor<EmptyEffect, TestState, TestError>.() -> Unit,
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
  ): AnchorRuntime<EmptyEffect, TestState, TestError> =
    createAnchor(
      init = init,
      defect = defect,
      subscriptions = {
        connect<SilentEvent.Ping> { events -> events.anchor { reduce { copy(value = 1) } } }
      },
    )

  /** Emits `Ping` and reports whether a subscription handled it in time. */
  private suspend fun AnchorRuntime<EmptyEffect, TestState, TestError>.pingHandled(): Int {
    emit { SilentEvent.Ping }
    withTimeoutOrNull(1_000) { viewState.first { it.value == 1 } }
    return viewState.value.value
  }

  // -- A1: a cancellation that does not come from cancelling the coroutine --

  @Test
  fun `a timeout inside an anchor action reaches defect and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<SilentEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) withTimeout(1) { awaitCancellation() }
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        anchor.emit { SilentEvent.Load(fail = false) }
        withTimeoutOrNull(1_000) { loads.first { it.isNotEmpty() } }

        assertEquals(
          Triple(listOf(false), listOf<String?>("TimeoutCancellationException"), 1),
          Triple(loads.value, defects.value.map { it::class.simpleName }, supervisor.children.count()),
        )
      }
    }

  @Test
  fun `awaiting a Deferred cancelled elsewhere inside an anchor action reaches defect`(): Unit =
    runBlocking {
      val cancelledElsewhere = CompletableDeferred<Unit>().apply { cancel() }
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<SilentEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) cancelledElsewhere.await()
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        anchor.emit { SilentEvent.Load(fail = false) }
        withTimeoutOrNull(1_000) { loads.first { it.isNotEmpty() } }

        assertEquals(
          Triple(listOf(false), 1, 1),
          Triple(loads.value, defects.value.size, supervisor.children.count()),
        )
      }
    }

  @Test
  fun `a timeout inside an inner flow ending in anchorErrors reaches defect and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<SilentEvent.Load> { events ->
            events
              .flatMapLatest { event ->
                flow {
                  if (event.fail) withTimeout(1) { awaitCancellation() }
                  emit(event.fail)
                }.anchorErrors()
              }.anchor { fail -> loads.update { it + fail } }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        // Wait for the timeout before the next event, which would cancel it.
        withTimeoutOrNull(1_000) { defects.first { it.isNotEmpty() } }
        anchor.emit { SilentEvent.Load(fail = false) }
        withTimeoutOrNull(1_000) { loads.first { it.isNotEmpty() } }

        assertEquals(
          Triple(listOf(false), listOf<String?>("TimeoutCancellationException"), 1),
          Triple(loads.value, defects.value.map { it::class.simpleName }, supervisor.children.count()),
        )
      }
    }

  @Test
  fun `a timeout inside an executed action reaches defect`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor = createAnchor(defect = { e -> defects.update { it + e } })

      anchor.inViewModel { viewModel ->
        viewModel.execute { withTimeout(1) { awaitCancellation() } }
        withTimeoutOrNull(1_000) { defects.first { it.isNotEmpty() } }

        assertEquals(listOf<String?>("TimeoutCancellationException"), defects.value.map { it::class.simpleName })
      }
    }

  @Test
  fun `a timeout in init reaches defect and leaves subscriptions attached`(): Unit =
    runBlocking {
      val initJob = CompletableDeferred<Job>()
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        pingAnchor(
          init = {
            initJob.complete(currentCoroutineContext().job)
            withTimeout(1) { awaitCancellation() }
          },
          defect = { e -> defects.update { it + e } },
        )

      anchor.inViewModel {
        withTimeoutOrNull(1_000) { initJob.await().join() }

        assertEquals(
          Triple(listOf<String?>("TimeoutCancellationException"), 1, 1),
          Triple(
            defects.value.map { it::class.simpleName },
            anchor.pingHandled(),
            anchor._emitter.subscriptionCount.value,
          ),
        )
      }
    }

  @Test
  fun `clearing the ViewModel while an action is suspended does not reach defect`(): Unit =
    runBlocking {
      val action = CompletableDeferred<Job>()
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor = createAnchor(defect = { e -> defects.update { it + e } })

      anchor.inViewModel { viewModel ->
        viewModel.execute {
          action.complete(currentCoroutineContext().job)
          awaitCancellation()
        }
        withTimeout(2_000) { action.await() }
      }
      val job = action.await()
      withTimeout(2_000) { job.join() }

      assertEquals(Pair(true, emptyList()), Pair(job.isCancelled, defects.value))
    }

  // -- A2: a domain error when onDomainError is absent --

  @Test
  fun `a domain error with only defect configured reaches defect as DomainDefectException and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(defect = { e -> defects.update { it + e } }) {
          connect<SilentEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) raise(TestError.NotFound)
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        anchor.emit { SilentEvent.Load(fail = false) }
        withTimeoutOrNull(1_000) { loads.first { it.isNotEmpty() } }

        assertEquals(
          Triple(listOf(false), listOf<Any?>(TestError.NotFound), 1),
          Triple(
            loads.value,
            defects.value.map { (it as? DomainDefectException)?.error },
            supervisor.children.count(),
          ),
        )
      }
    }

  @Test
  fun `a domain error in an executed action with only defect configured reaches defect`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor = createAnchor(defect = { e -> defects.update { it + e } })

      anchor.inViewModel { viewModel ->
        viewModel.execute {
          @Suppress("UNCHECKED_CAST")
          (this as Anchor<EmptyEffect, TestState, TestError>).raise(TestError.NotFound)
        }
        withTimeoutOrNull(1_000) { defects.first { it.isNotEmpty() } }

        assertEquals(listOf<Any?>(TestError.NotFound), defects.value.map { (it as? DomainDefectException)?.error })
      }
    }

  @Test
  fun `a domain error in init with only defect configured reaches defect and leaves subscriptions attached`(): Unit =
    runBlocking {
      val initJob = CompletableDeferred<Job>()
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        pingAnchor(
          init = {
            initJob.complete(currentCoroutineContext().job)
            raise(TestError.NotFound)
          },
          defect = { e -> defects.update { it + e } },
        )

      anchor.inViewModel {
        withTimeoutOrNull(1_000) { initJob.await().join() }

        assertEquals(
          Triple(listOf<Any?>(TestError.NotFound), 1, 1),
          Triple(
            defects.value.map { (it as? DomainDefectException)?.error },
            anchor.pingHandled(),
            anchor._emitter.subscriptionCount.value,
          ),
        )
      }
    }

  @Test
  fun `a domain error with no handlers still stops the action silently`(): Unit =
    runBlocking {
      val anchor = createAnchor()

      val (job, escaped) = anchor.executeMirror { raise(TestError.NotFound) }

      assertEquals(Pair(true, null), Pair(job.isCancelled, escaped))
    }

  @Test
  fun `a timeout with no defect still stops the action silently`(): Unit =
    runBlocking {
      val anchor = createAnchor()

      val (job, escaped) = anchor.executeMirror { withTimeout(1) { awaitCancellation() } }

      assertEquals(Pair(true, null), Pair(job.isCancelled, escaped))
    }

  // -- A3: an error handler that throws is invoked once --

  @Test
  fun `a defect handler that throws inside an anchor action is invoked once`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            throw IllegalStateException("defect handler failed")
          },
        ) {
          connect<SilentEvent.Load> { events -> events.anchor { throw SilentFailure("action failed") } }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(0)

        assertEquals(listOf<String?>("action failed"), defects.value)
      }
    }

  @Test
  fun `a defect handler that throws inside an anchor action of a flatMapLatest inner flow is invoked once`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            throw IllegalStateException("defect handler failed")
          },
        ) {
          connect<SilentEvent.Load> { events ->
            events.flatMapLatest { event ->
              flowOf(event).anchor { throw SilentFailure("action failed") }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(0)

        assertEquals(listOf<String?>("action failed"), defects.value)
      }
    }

  @Test
  fun `a defect handler that throws inside anchorErrors is invoked once`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            throw IllegalStateException("defect handler failed")
          },
        ) {
          connect<SilentEvent.Load> { events ->
            events.flatMapLatest {
              flow<Int> { throw SilentFailure("fetch failed") }.anchorErrors()
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(0)

        assertEquals(listOf<String?>("fetch failed"), defects.value)
      }
    }

  @Test
  fun `a defect handler that throws inside an anchor action followed by anchorErrors is invoked once`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            throw IllegalStateException("defect handler failed")
          },
        ) {
          connect<SilentEvent.Load> { events ->
            events.flatMapLatest { event ->
              flowOf(event).anchor { throw SilentFailure("action failed") }.anchorErrors()
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(0)

        assertEquals(listOf<String?>("action failed"), defects.value)
      }
    }

  @Test
  fun `an exception that wraps an escaped one is routed as a new failure`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            if (e is SilentFailure) throw IllegalStateException("defect handler failed")
          },
        ) {
          connect<SilentEvent.Load> { events ->
            events
              .anchor { throw SilentFailure("action failed") }
              .catch { e -> throw WrappedFailure("wrapped", e) }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        supervisor.awaitLiveListeners(0)

        assertEquals(listOf<String?>("action failed", "wrapped"), defects.value)
      }
    }

  @Test
  fun `a throwing defect handler in an executed action runs once and its exception escapes`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          defect = { e ->
            defects.update { it + e.message }
            throw IllegalStateException("defect handler failed")
          },
        )

      val (_, escaped) = anchor.executeMirror { throw SilentFailure("action failed") }

      assertEquals(
        Pair(listOf<String?>("action failed"), "defect handler failed"),
        Pair(defects.value, escaped?.message),
      )
    }

  @Test
  fun `an onDomainError that throws is routed to defect once and the listener survives`(): Unit =
    runBlocking {
      val loads = MutableStateFlow<List<Boolean>>(emptyList())
      val defects = MutableStateFlow<List<String?>>(emptyList())
      val anchor =
        createAnchor(
          onDomainError = { throw IllegalStateException("onDomainError failed") },
          defect = { e -> defects.update { it + e.message } },
        ) {
          connect<SilentEvent.Load> { events ->
            events.anchor { event ->
              if (event.fail) raise(TestError.NotFound)
              loads.update { it + event.fail }
            }
          }
        }

      anchor.withListeners(listeners = 1) { supervisor ->
        anchor.emit { SilentEvent.Load(fail = true) }
        anchor.emit { SilentEvent.Load(fail = false) }
        withTimeoutOrNull(1_000) { loads.first { it.isNotEmpty() } }

        assertEquals(
          Triple(listOf(false), listOf<String?>("onDomainError failed"), 1),
          Triple(loads.value, defects.value, supervisor.children.count()),
        )
      }
    }

  // -- A4: subscriptions outlive init --

  @Test
  fun `a domain error in init with no handlers leaves subscriptions attached`(): Unit =
    runBlocking {
      val initJob = CompletableDeferred<Job>()
      val anchor =
        pingAnchor(
          init = {
            initJob.complete(currentCoroutineContext().job)
            raise(TestError.NotFound)
          },
        )

      anchor.inViewModel {
        withTimeoutOrNull(1_000) { initJob.await().join() }

        assertEquals(Pair(1, 1), Pair(anchor.pingHandled(), anchor._emitter.subscriptionCount.value))
      }
    }

  @Test
  fun `a clean init leaves handlers attached`(): Unit =
    runBlocking {
      val initDone = CompletableDeferred<Unit>()
      val anchor = pingAnchor(init = { initDone.complete(Unit) })

      anchor.inViewModel {
        withTimeout(2_000) { initDone.await() }

        assertEquals(Pair(1, 1), Pair(anchor.pingHandled(), anchor._emitter.subscriptionCount.value))
      }
    }
}
