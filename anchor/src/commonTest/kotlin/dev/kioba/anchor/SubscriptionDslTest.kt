package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
