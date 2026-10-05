package dev.kioba.anchor.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

private suspend fun TestAnchor.incrementThenComplete(done: CompletableDeferred<Unit>) {
  reduce { copy(count = count + 1) }
  done.complete(Unit)
}

@OptIn(ExperimentalTestApi::class)
class RememberAnchorTest {
  @Test
  fun `RememberAnchor renders initial state`() =
    runComposeUiTest {
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "initial-state") {
          BasicText("count:${state.count}")
        }
      }
      onNodeWithText("count:0").assertExists()
    }

  @Test
  fun `anchor callback executes action and updates state`() =
    runComposeUiTest {
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "anchor-callback") {
          Column {
            BasicText("count:${state.count}")
            TestButton("increment", anchor(TestAnchor::increment))
          }
        }
      }
      onNodeWithText("increment").performClick()
      // The action runs on Dispatchers.Default: wait for the new state instead of asserting at once.
      waitUntilExactlyOneExists(hasText("count:1"), timeoutMillis = 5_000)
    }

  @Test
  fun `HandleSignal receives a posted signal`() =
    runComposeUiTest {
      val received = CopyOnWriteArrayList<Int>()
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "handle-signal") {
          HandleSignal<TestSignal.Toast> { received += it.n }
          val postToast = anchor(TestAnchor::postToast)
          TestButton("toast") { postToast(7) }
        }
      }
      onNodeWithText("toast").performClick()
      waitUntil(timeoutMillis = 5_000) { received.isNotEmpty() }
      drainUiThread()
      assertEquals(listOf(7), received.toList())
    }

  @Test
  fun `state collection pauses below STARTED and catches up on resume`() =
    runComposeUiTest {
      val lifecycle = TestLifecycleOwner()
      var incrementThenComplete: ((CompletableDeferred<Unit>) -> Unit)? = null
      setContentWithLifecycle(lifecycle) {
        RememberAnchor(scope = { testAnchor() }, customKey = "lifecycle") {
          incrementThenComplete = anchor(TestAnchor::incrementThenComplete)
          BasicText("count:${state.count}")
        }
      }
      onNodeWithText("count:0").assertExists()

      moveLifecycleTo(lifecycle, Lifecycle.State.CREATED)
      val reduced = CompletableDeferred<Unit>()
      incrementThenComplete!!(reduced)
      waitUntil(timeoutMillis = 5_000) { reduced.isCompleted }
      drainUiThread()
      onNodeWithText("count:0").assertExists()

      moveLifecycleTo(lifecycle, Lifecycle.State.RESUMED)
      waitUntilExactlyOneExists(hasText("count:1"), timeoutMillis = 5_000)
    }
}
