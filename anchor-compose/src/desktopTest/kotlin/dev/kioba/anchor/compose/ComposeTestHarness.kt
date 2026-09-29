package dev.kioba.anchor.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.util.concurrent.atomic.AtomicInteger

// Test harness for anchor-compose, on top of androidx.compose.ui.test.v2.runComposeUiTest.
//
// The v2 test host already provides a ViewModelStoreOwner and a RESUMED LifecycleOwner, so
// RememberAnchor works unwrapped. Actions run on Dispatchers.Default and state is collected on
// Dispatchers.Main (the Swing EDT, via kotlinx-coroutines-swing), so wait for results with
// waitUntil rather than asserting right after triggering an action. Give every RememberAnchor in a
// test its own customKey so each gets its own ViewModel.

/**
 * A [LifecycleOwner] the test moves with [moveLifecycleTo], e.g. below STARTED and back.
 *
 * The test host's own owner stays RESUMED. Install this one with [setContentWithLifecycle].
 */
class TestLifecycleOwner(
  initialState: Lifecycle.State = Lifecycle.State.RESUMED,
) : LifecycleOwner {
  // createUnsafe skips the main-thread check. moveLifecycleTo still confines changes to the UI
  // thread, where repeatOnLifecycle registers its observers.
  internal val registry: LifecycleRegistry =
    LifecycleRegistry.createUnsafe(this).apply { currentState = initialState }

  override val lifecycle: Lifecycle
    get() = registry
}

/** Sets [content] under [owner] instead of the test host's always-RESUMED lifecycle. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.setContentWithLifecycle(
  owner: LifecycleOwner,
  content: @Composable () -> Unit,
) {
  setContent {
    CompositionLocalProvider(LocalLifecycleOwner provides owner, content = content)
  }
}

/** Moves [owner] to [state] on the UI thread, where lifecycle observers run, then waits for idle. */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.moveLifecycleTo(
  owner: TestLifecycleOwner,
  state: Lifecycle.State,
) {
  runOnUiThread { owner.registry.currentState = state }
  waitForIdle()
}

/**
 * Runs everything already queued on the UI thread, then waits for idle.
 *
 * Collectors on Dispatchers.Main resume through the EDT queue, so after this returns any update
 * that was emitted before the call has reached the composition. Use it before asserting that
 * something did *not* happen.
 */
@OptIn(ExperimentalTestApi::class)
fun ComposeUiTest.drainUiThread() {
  runOnUiThread {}
  waitForIdle()
}

/**
 * Counts the committed compositions of the restart scope that calls [record].
 *
 * [record] is non-restartable, so it joins the caller's scope: every composition of that scope,
 * first or recomposition, adds one.
 */
class CompositionCounter {
  private val count = AtomicInteger(0)

  val value: Int
    get() = count.get()

  @Composable
  @NonRestartableComposable
  fun record() {
    SideEffect { count.incrementAndGet() }
  }
}

/** A clickable text node, so tests can drive `anchor()` callbacks through `performClick()`. */
@Composable
fun TestButton(
  label: String,
  onClick: () -> Unit,
) {
  BasicText(text = label, modifier = Modifier.clickable(onClick = onClick))
}
