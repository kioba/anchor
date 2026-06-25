package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private sealed interface SubTestEvent : Event {
  data class Crash(val value: Int) : SubTestEvent

  data class Survive(val value: Int) : SubTestEvent
}

class SubscriptionDslTest {

  private fun createAnchor() =
    AnchorRuntime<EmptyEffect, TestState, TestError>(
      initialState = { TestState(value = 0) },
      effectScope = { EmptyEffect },
    )

  private fun createScope(anchor: AnchorRuntime<EmptyEffect, TestState, TestError>) =
    SubscriptionsScope(
      chain = emptyFlow(),
      anchor = anchor,
      effect = EmptyEffect,
    )

  private suspend fun Anchor<EmptyEffect, TestState, TestError>.setValueSuspend(newValue: Int) {
    reduce { copy(value = newValue) }
  }

  @Test
  fun `anchor with suspend action updates state`(): Unit =
    runBlocking {
      val anchor = createAnchor()

      with(createScope(anchor)) {
        flowOf(42)
          .anchor { value -> setValueSuspend(value) }
          .collect {}
      }

      assertEquals(42, anchor.state.value)
    }

  @Test
  fun `anchor with non-suspend action still works`(): Unit =
    runBlocking {
      val anchor = createAnchor()

      with(createScope(anchor)) {
        flowOf(99)
          .anchor { value -> reduce { copy(value = value) } }
          .collect {}
      }

      assertEquals(99, anchor.state.value)
    }

  @Test
  fun `sibling subscription keeps receiving events when another subscription throws`(): Unit =
    runBlocking {
      val survivedValues = mutableListOf<Int>()
      val anchor =
        AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
          subscriptions = {
            connect<SubTestEvent.Crash> { events ->
              events.map<SubTestEvent.Crash, Unit> { event ->
                throw IllegalStateException("subscription boom: ${event.value}")
              }
            }
            connect<SubTestEvent.Survive> { events ->
              events.onEach { event -> survivedValues.add(event.value) }
            }
          },
        )

      coroutineScope {
        val supervisorReady = CompletableDeferred<Job>()
        launch {
          with(anchor) {
            supervisorReady.complete(subscribe())
          }
        }
        val supervisor = supervisorReady.await()

        // Wait for both connect() flows to start collecting from _emitter.
        withTimeout(2_000) {
          while (anchor._emitter.subscriptionCount.value < 2) yield()
        }

        // Trigger the crashing subscription. Before the supervisor isolation
        // fix, this also tears down the surviving subscription.
        anchor._emitter.emit(SubTestEvent.Crash(value = 1))

        // The surviving subscription must still receive new events.
        anchor._emitter.emit(SubTestEvent.Survive(value = 7))
        anchor._emitter.emit(SubTestEvent.Survive(value = 11))

        withTimeout(2_000) {
          while (survivedValues.size < 2) yield()
        }

        assertEquals(listOf(7, 11), survivedValues)
        assertTrue(supervisor.isActive, "supervisor must stay active when a child throws")

        supervisor.cancelAndJoin()
      }
    }
}
