package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.ContainerViewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

private sealed interface InitEvent : Event {
  data object Setup : InitEvent

  data object Ping : InitEvent

  data object Done : InitEvent
}

/**
 * Runs nothing dispatched to it until [drain] starts, standing in for a busy
 * dispatcher that a handler moves its collection to.
 */
private class HeldDispatcher : CoroutineDispatcher() {
  private val held = Channel<Runnable>(Channel.UNLIMITED)

  override fun dispatch(
    context: CoroutineContext,
    block: Runnable,
  ) {
    held.trySend(block)
  }

  suspend fun drain() {
    for (block in held) block.run()
  }

  fun close() {
    held.close()
  }
}

class InitEventDeliveryTest {

  private fun createAnchor(
    init: (suspend Anchor<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<EmptyEffect, TestState, TestError>.() -> Unit)? = null,
    onDomainError: (suspend ErrorScope<EmptyEffect, TestState>.(TestError) -> Unit)? = null,
    defect: (suspend ErrorScope<EmptyEffect, TestState>.(Throwable) -> Unit)? = null,
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
   * Starts the anchor in a [ContainerViewModel], the way `RememberAnchor` and
   * iOS `rememberAnchor` do, runs [block], and clears the ViewModel afterwards
   * so no subscription outlives the test.
   */
  private suspend fun AnchorRuntime<EmptyEffect, TestState, TestError>.inViewModel(
    block: suspend () -> Unit,
  ) {
    val store = ViewModelStore()
    val owner =
      object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
      }
    val runtime = this
    val provider = ViewModelProvider.create(owner, ContainerViewModelFactory { ContainerViewModel(runtime) })
    provider[ContainerViewModel::class]
    try {
      block()
    } finally {
      store.clear()
    }
  }

  private suspend fun <T : Any> awaitOrFail(
    message: String,
    block: suspend () -> T,
  ): T =
    withTimeoutOrNull(2_000) { block() } ?: fail(message)

  @Test
  fun `an event emitted from init reaches its connect handler`(): Unit =
    runBlocking {
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            connect<InitEvent.Setup> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
        )

      anchor.inViewModel {
        awaitOrFail("the Setup event emitted from init never reached its handler") {
          anchor.viewState.first { it.value == 1 }
        }
      }
    }

  @Test
  fun `a handler receives Created before the events init emits`(): Unit =
    runBlocking {
      val received = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            connect<Event> { events -> events.onEach { event -> received.update { it + event } } }
          },
        )

      anchor.inViewModel {
        val events =
          awaitOrFail("the handler did not receive both Created and Setup") {
            received.first { it.size >= 2 }
          }
        assertEquals(listOf(Created, InitEvent.Setup), events)
      }
    }

  @Test
  fun `a domain error raised in init does not skip subscriptions`(): Unit =
    runBlocking {
      val errors = MutableStateFlow<List<TestError>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(
          init = { raise(TestError.NotFound) },
          subscriptions = {
            connect<InitEvent.Ping> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
          onDomainError = { error -> errors.update { it + error } },
          defect = { throwable -> defects.update { it + throwable } },
        )

      anchor.inViewModel {
        awaitOrFail("init's domain error never reached onDomainError") {
          errors.first { it.isNotEmpty() }
        }

        anchor.emit { InitEvent.Ping }

        awaitOrFail("no subscription handled an event emitted after init raised") {
          anchor.viewState.first { it.value == 1 }
        }
        assertEquals(listOf<TestError>(TestError.NotFound), errors.value)
        assertEquals(emptyList(), defects.value)
      }
    }

  @Test
  fun `a defect thrown in init does not skip subscriptions`(): Unit =
    runBlocking {
      val errors = MutableStateFlow<List<TestError>>(emptyList())
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(
          init = { throw IllegalStateException("init boom") },
          subscriptions = {
            connect<InitEvent.Ping> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
          onDomainError = { error -> errors.update { it + error } },
          defect = { throwable -> defects.update { it + throwable } },
        )

      anchor.inViewModel {
        awaitOrFail("init's defect never reached the defect handler") {
          defects.first { it.isNotEmpty() }
        }

        anchor.emit { InitEvent.Ping }

        awaitOrFail("no subscription handled an event emitted after init threw") {
          anchor.viewState.first { it.value == 1 }
        }
        assertEquals(listOf("init boom"), defects.value.map { it.message })
        assertEquals(emptyList(), errors.value)
      }
    }

  @Test
  fun `a domain error raised in subscription setup does not skip init`(): Unit =
    runBlocking {
      val errors = MutableStateFlow<List<TestError>>(emptyList())
      val anchor =
        createAnchor(
          init = { reduce { copy(value = 1) } },
          subscriptions = { anchor.raise(TestError.NotFound) },
          onDomainError = { error -> errors.update { it + error } },
        )

      anchor.inViewModel {
        // init runs first, so wait for the later step: the error from subscription setup.
        awaitOrFail("the domain error from subscription setup never reached onDomainError") {
          errors.first { it.isNotEmpty() }
        }
        assertEquals(1, anchor.viewState.value.value)
        assertEquals(listOf<TestError>(TestError.NotFound), errors.value)
      }
    }

  @Test
  fun `init runs when there are no subscriptions`(): Unit =
    runBlocking {
      val anchor = createAnchor(init = { reduce { copy(value = 1) } })

      anchor.inViewModel {
        awaitOrFail("init did not run without subscriptions") {
          anchor.viewState.first { it.value == 1 }
        }
      }
    }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun `a handler that subscribes through flatMapLatest receives init events`(): Unit =
    runBlocking {
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            // flatMapLatest collects its upstream in a separately dispatched
            // coroutine, so this handler subscribes to the bus asynchronously.
            connect<InitEvent.Setup> { events ->
              events
                .flatMapLatest { flowOf(1) }
                .anchor { value -> reduce { copy(value = value) } }
            }
          },
        )

      anchor.inViewModel {
        awaitOrFail("the Setup event emitted from init never reached the flatMapLatest handler") {
          anchor.viewState.first { it.value == 1 }
        }
      }
    }

  @Test
  fun `a handler that ends before subscribing does not hold up the other handlers`(): Unit =
    runBlocking {
      val defects = MutableStateFlow<List<Throwable>>(emptyList())
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            connect<InitEvent.Setup> { events ->
              events.onStart { throw IllegalStateException("handler boom") }
            }
            connect<InitEvent.Setup> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
          defect = { throwable -> defects.update { it + throwable } },
        )

      anchor.inViewModel {
        awaitOrFail("the Setup event from init never reached the live handler") {
          anchor.viewState.first { it.value == 1 }
        }
        assertEquals(listOf("handler boom"), defects.value.map { it.message })
      }
    }

  @Test
  fun `a Created handler sees the state init set`(): Unit =
    runBlocking {
      val seen = CompletableDeferred<Int>()
      val anchor =
        createAnchor(
          init = { reduce { copy(value = 5) } },
          subscriptions = {
            connect<Created> { events -> events.anchor { seen.complete(state.value) } }
          },
        )

      anchor.inViewModel {
        val value = awaitOrFail("the Created handler never ran") { seen.await() }
        assertEquals(5, value)
      }
    }

  @Test
  fun `a handler that never collects its events does not hold up startup`(): Unit =
    runBlocking {
      val repository = MutableStateFlow(7)
      val observed = MutableStateFlow<List<Int>>(emptyList())
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            // Ignores its events and never completes, like
            // connect<E> { repository.observe().anchor(...) }.
            connect<InitEvent.Setup> { _ -> repository.anchor { value -> observed.update { it + value } } }
            connect<InitEvent.Setup> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
        )

      anchor.inViewModel {
        awaitOrFail("the Setup event from init never reached the handler collecting its events") {
          anchor.viewState.first { it.value == 1 }
        }
        assertEquals(listOf(7), observed.value)
      }
    }

  @Test
  fun `a handler that buffers its events receives init events`(): Unit =
    runBlocking {
      val received = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            // buffer() collects its upstream in a coroutine of its own.
            connect<Event> { events -> events.buffer().onEach { event -> received.update { it + event } } }
          },
        )

      anchor.inViewModel {
        val events =
          awaitOrFail("the buffered handler did not receive both Created and Setup") {
            received.first { it.size >= 2 }
          }
        assertEquals(listOf(Created, InitEvent.Setup), events)
      }
    }

  @Test
  fun `a handler that subscribes on another dispatcher misses events emitted before it attaches`(): Unit =
    runBlocking {
      val held = HeldDispatcher()
      val received = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            // Starts in place, so it receives Setup when the startup queue is flushed.
            connect<InitEvent.Setup> { events -> events.anchor { reduce { copy(value = 1) } } }
            connect<Event> { events -> events.flowOn(held).onEach { event -> received.update { it + event } } }
          },
        )

      try {
        anchor.inViewModel {
          awaitOrFail("the startup queue never flushed Setup to the handler started in place") {
            anchor.viewState.first { it.value == 1 }
          }

          // flowOn collects upstream on its dispatcher, so the handler
          // subscribes only once that dispatcher runs, after the flush.
          launch(Dispatchers.Default) { held.drain() }
          awaitOrFail("the flowOn handler never subscribed") {
            anchor._emitter.subscriptionCount.first { it >= 2 }
          }
          anchor.emit { InitEvent.Ping }

          val events =
            awaitOrFail("the flowOn handler did not receive Created and Ping") {
              received.first { it.size >= 2 }
            }
          assertEquals(listOf(Created, InitEvent.Ping), events)
        }
      } finally {
        held.close()
      }
    }

  @Test
  fun `an event a Created handler emits during startup reaches a handler declared after it`(): Unit =
    runBlocking {
      val anchor =
        createAnchor(
          subscriptions = {
            connect<Created> { events -> events.anchor { emit { InitEvent.Ping } } }
            connect<InitEvent.Ping> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
        )

      anchor.inViewModel {
        awaitOrFail("the Ping a Created handler emitted during startup never reached the Ping handler") {
          anchor.viewState.first { it.value == 1 }
        }
      }
    }

  @Test
  fun `an event chain started by init reaches a handler declared after the one that emits`(): Unit =
    runBlocking {
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            connect<InitEvent.Setup> { events -> events.anchor { emit { InitEvent.Ping } } }
            connect<InitEvent.Ping> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
        )

      anchor.inViewModel {
        awaitOrFail("the Ping emitted while handling Setup from init never reached the Ping handler") {
          anchor.viewState.first { it.value == 1 }
        }
      }
    }

  @Test
  fun `an event emitted while init runs is delivered after the events init emitted`(): Unit =
    runBlocking {
      val initStarted = CompletableDeferred<Unit>()
      val releaseInit = CompletableDeferred<Unit>()
      val received = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(
          init = {
            emit { InitEvent.Setup }
            initStarted.complete(Unit)
            releaseInit.await()
          },
          subscriptions = {
            connect<Event> { events -> events.onEach { event -> received.update { it + event } } }
          },
        )

      anchor.inViewModel {
        awaitOrFail("init never started") { initStarted.await() }
        // Stands in for a UI action that emits while init is still running.
        anchor.emit { InitEvent.Ping }
        releaseInit.complete(Unit)

        val events =
          awaitOrFail("the handler did not receive Created, Setup and Ping") {
            received.first { it.size >= 3 }
          }
        assertEquals(listOf(Created, InitEvent.Setup, InitEvent.Ping), events)
      }
    }

  @Test
  fun `every handler receives the events queued during startup`(): Unit =
    runBlocking {
      val first = MutableStateFlow<List<Event>>(emptyList())
      val second = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(
          init = {
            emit { InitEvent.Setup }
            emit { InitEvent.Ping }
          },
          subscriptions = {
            connect<Event> { events -> events.onEach { event -> first.update { it + event } } }
            connect<Event> { events -> events.onEach { event -> second.update { it + event } } }
          },
        )

      anchor.inViewModel {
        val expected = listOf(Created, InitEvent.Setup, InitEvent.Ping)
        val firstEvents = awaitOrFail("the first handler missed queued events") { first.first { it.size >= 3 } }
        val secondEvents = awaitOrFail("the second handler missed queued events") { second.first { it.size >= 3 } }
        assertEquals(expected, firstEvents)
        assertEquals(expected, secondEvents)
      }
    }

  @Test
  fun `a handler that subscribes again receives Created but not the startup events again`(): Unit =
    runBlocking {
      val received = MutableStateFlow<List<Event>>(emptyList())
      var failedOnce = false
      val anchor =
        createAnchor(
          init = { emit { InitEvent.Setup } },
          subscriptions = {
            connect<Event> { events ->
              events
                .onEach { event -> received.update { it + event } }
                .onEach { event ->
                  if (event == InitEvent.Setup && !failedOnce) {
                    failedOnce = true
                    throw IllegalStateException("fail once")
                  }
                }.retry(1)
            }
          },
        )

      anchor.inViewModel {
        awaitOrFail("the handler never subscribed again") { received.first { it.size >= 3 } }
        anchor.emit { InitEvent.Ping }

        val events =
          awaitOrFail("the handler did not receive the live Ping after subscribing again") {
            received.first { InitEvent.Ping in it }
          }
        assertEquals(listOf(Created, InitEvent.Setup, Created, InitEvent.Ping), events)
      }
    }

  @Test
  fun `an event a handler emits during the flush arrives after every queued event`(): Unit =
    runBlocking {
      val received = MutableStateFlow<List<Event>>(emptyList())
      val anchor =
        createAnchor(
          init = {
            emit { InitEvent.Setup }
            repeat(100) { emit { InitEvent.Ping } }
          },
          subscriptions = {
            // More Pings queue behind Setup than the bus buffers, so the flush
            // waits on this handler while its action emits Done.
            connect<InitEvent.Setup> { events -> events.anchor { emit { InitEvent.Done } } }
            connect<Event> { events -> events.onEach { event -> received.update { it + event } } }
          },
        )

      anchor.inViewModel {
        val events =
          awaitOrFail("the flush deadlocked, or Done never arrived") {
            received.first { InitEvent.Done in it }
          }
        assertEquals(listOf(Created, InitEvent.Setup) + List(100) { InitEvent.Ping } + InitEvent.Done, events)
      }
    }

  @Test
  fun `emit goes live even when subscription setup raises`(): Unit =
    runBlocking {
      val errors = MutableStateFlow<List<TestError>>(emptyList())
      val anchor =
        createAnchor(
          subscriptions = { anchor.raise(TestError.NotFound) },
          onDomainError = { error -> errors.update { it + error } },
        )

      anchor.inViewModel {
        awaitOrFail("the domain error from subscription setup never reached onDomainError") {
          errors.first { it.isNotEmpty() }
        }

        // Watch the bus directly: if emit still queued, Ping would never reach it.
        val subscribed = CompletableDeferred<Unit>()
        val onBus =
          async(Dispatchers.Default) {
            anchor._emitter
              .onSubscription { subscribed.complete(Unit) }
              .first { it == InitEvent.Ping }
          }
        awaitOrFail("the bus watcher never subscribed") { subscribed.await() }
        anchor.emit { InitEvent.Ping }

        awaitOrFail("Ping never reached the bus after subscription setup raised") { onBus.await() }
      }
    }
}
