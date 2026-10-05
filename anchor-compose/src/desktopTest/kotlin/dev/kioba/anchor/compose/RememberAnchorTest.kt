package dev.kioba.anchor.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

private suspend fun TestAnchor.incrementThenComplete(done: CompletableDeferred<Unit>) {
  reduce { copy(count = count + 1) }
  done.complete(Unit)
}

@Composable
private fun CountSlice(
  scope: AnchorStateScope<TestState>,
  counter: CompositionCounter,
) {
  CountText(count = scope.collectState { it.count }, counter = counter)
}

@Composable
private fun CountText(
  count: Int,
  counter: CompositionCounter,
) {
  counter.record()
  BasicText("count:$count")
}

// A separate restart scope that reads the whole state, so the test can wait for a label update
// without reading state in CountSlice.
@Composable
private fun LabelObserver(
  scope: AnchorStateScope<TestState>,
  lastLabel: AtomicReference<String>,
) {
  lastLabel.set(scope.state.label)
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
  fun `collectState only exposes the selected slice`() =
    runComposeUiTest {
      // Counts the consumer of the slice. Whether the collectState caller itself skips is #143.
      val countText = CompositionCounter()
      val lastLabel = AtomicReference("")
      var setLabel: ((String) -> Unit)? = null
      var increment: (() -> Unit)? = null
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "collect-state") {
          setLabel = anchor(TestAnchor::setLabel)
          increment = anchor(TestAnchor::increment)
          CountSlice(this, countText)
          LabelObserver(this, lastLabel)
        }
      }
      waitForIdle()
      assertEquals(1, countText.value)

      repeat(3) { i ->
        setLabel!!("label-$i")
        waitUntil(timeoutMillis = 5_000) { lastLabel.get() == "label-$i" }
      }
      drainUiThread()
      onNodeWithText("count:0").assertExists()
      assertEquals(1, countText.value, "the count consumer recomposed for label-only changes")

      increment!!()
      waitUntilExactlyOneExists(hasText("count:1"), timeoutMillis = 5_000)
      assertEquals(2, countText.value)
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
