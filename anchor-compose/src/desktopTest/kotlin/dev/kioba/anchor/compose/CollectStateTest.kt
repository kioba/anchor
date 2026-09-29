package dev.kioba.anchor.compose

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.waitUntilExactlyOneExists
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

// The collectState caller. [caller] counts this restart scope, [consumer] counts CountText's.
@Composable
private fun CountSlice(
  scope: AnchorStateScope<TestState>,
  caller: CompositionCounter,
  consumer: CompositionCounter,
) {
  caller.record()
  CountText(count = scope.collectState { it.count }, counter = consumer)
}

@Composable
private fun CountText(
  count: Int,
  counter: CompositionCounter,
) {
  counter.record()
  BasicText("count:$count")
}

// The selector captures a composition value, so a new offset means a new selector.
@Composable
private fun OffsetSlice(
  scope: AnchorStateScope<TestState>,
  offset: Int,
  last: AtomicInteger,
) {
  last.set(scope.collectState { it.count + offset })
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
class CollectStateTest {
  @Test
  fun `caller does not recompose when an unselected field changes`() =
    runComposeUiTest {
      val caller = CompositionCounter()
      val consumer = CompositionCounter()
      val lastLabel = AtomicReference("")
      var setLabel: ((String) -> Unit)? = null
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "unselected-field") {
          setLabel = anchor(TestAnchor::setLabel)
          CountSlice(this, caller, consumer)
          LabelObserver(this, lastLabel)
        }
      }
      waitForIdle()
      assertEquals(1, caller.value)
      assertEquals(1, consumer.value)

      repeat(3) { i ->
        setLabel!!("label-$i")
        waitUntil(timeoutMillis = 5_000) { lastLabel.get() == "label-$i" }
      }
      drainUiThread()
      onNodeWithText("count:0").assertExists()
      assertEquals(1, caller.value, "the collectState caller recomposed for label-only changes")
      assertEquals(1, consumer.value, "the count consumer recomposed for label-only changes")
    }

  @Test
  fun `selected value change recomposes the caller once`() =
    runComposeUiTest {
      val caller = CompositionCounter()
      val consumer = CompositionCounter()
      var increment: (() -> Unit)? = null
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "selected-field") {
          increment = anchor(TestAnchor::increment)
          CountSlice(this, caller, consumer)
        }
      }
      waitForIdle()
      assertEquals(1, caller.value)

      increment!!()
      waitUntilExactlyOneExists(hasText("count:1"), timeoutMillis = 5_000)
      drainUiThread()
      assertEquals(2, caller.value)
      assertEquals(2, consumer.value)
    }

  @Test
  fun `selector change is honored without a state change`() =
    runComposeUiTest {
      val last = AtomicInteger(-1)
      var offset by mutableIntStateOf(0)
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "selector-change") {
          OffsetSlice(this, offset, last)
        }
      }
      waitForIdle()
      assertEquals(0, last.get())

      offset = 10
      waitForIdle()
      assertEquals(10, last.get(), "collectState returned a stale value after the selector changed")
    }
}
