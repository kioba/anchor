package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.lifecycle.Lifecycle
import dev.kioba.anchor.SignalProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

private suspend fun TestAnchor.postThenComplete(
  signal: TestSignal,
  done: CompletableDeferred<Unit>,
) {
  post { signal }
  done.complete(Unit)
}

/**
 * Counts the collectors attached to the anchor's signal stream, so a test can wait for a
 * HandleSignal to attach (or detach) before posting. Install it with [CountSubscribers].
 */
private class SignalSubscribers {
  private val attached = AtomicInteger(0)

  val count: Int
    get() = attached.get()

  // onSubscription runs once the upstream subscription is registered, so a signal posted after
  // count goes up reaches the collector. The decrement runs after the upstream slot is freed.
  fun wrap(signals: Flow<SignalProvider>): Flow<SignalProvider> =
    flow {
      var subscribed = false
      try {
        emitAll(
          (signals as SharedFlow<SignalProvider>).onSubscription {
            subscribed = true
            attached.incrementAndGet()
          },
        )
      } finally {
        if (subscribed) attached.decrementAndGet()
      }
    }
}

/** Provides [content] with the enclosing RememberAnchor's signal stream, counted by [subscribers]. */
@Composable
private fun CountSubscribers(
  subscribers: SignalSubscribers,
  content: @Composable () -> Unit,
) {
  val signals = LocalSignals.current
  val counted = remember(signals) { subscribers.wrap(signals) }
  CompositionLocalProvider(LocalSignals provides counted, content = content)
}

/** Posts [signal] through [post] and waits until the action has emitted it. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.postAndAwait(
  post: (TestSignal, CompletableDeferred<Unit>) -> Unit,
  signal: TestSignal,
) {
  val done = CompletableDeferred<Unit>()
  post(signal, done)
  waitUntil(conditionDescription = "$signal posted", timeoutMillis = 5_000) { done.isCompleted }
}

@OptIn(ExperimentalTestApi::class)
class HandleSignalTest {
  @Test
  fun `burst of signals are all handled`() =
    runComposeUiTest {
      val subscribers = SignalSubscribers()
      val received = CopyOnWriteArrayList<Int>()
      lateinit var postSignals: (List<TestSignal>) -> Unit
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "burst") {
          CountSubscribers(subscribers) {
            HandleSignal<TestSignal.Toast> { received += it.n }
            postSignals = anchor(TestAnchor::postSignals)
          }
        }
      }
      waitUntil(conditionDescription = "collector attached", timeoutMillis = 5_000) { subscribers.count == 1 }

      postSignals(listOf(TestSignal.Toast(1), TestSignal.Toast(2), TestSignal.Toast(3)))
      waitUntil(conditionDescription = "last signal handled", timeoutMillis = 5_000) { 3 in received }
      drainUiThread()
      assertEquals(listOf(1, 2, 3), received.toList())
    }

  @Test
  fun `slow handler is not cancelled by next signal`() =
    runComposeUiTest {
      val subscribers = SignalSubscribers()
      val started = CopyOnWriteArrayList<Int>()
      val completed = CopyOnWriteArrayList<Int>()
      // Holds the first handler mid-flight until the second signal has been posted.
      val release = CompletableDeferred<Unit>()
      lateinit var post: (TestSignal, CompletableDeferred<Unit>) -> Unit
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "slow-handler") {
          CountSubscribers(subscribers) {
            HandleSignal<TestSignal.Toast> {
              started += it.n
              if (it.n == 1) release.await()
              completed += it.n
            }
            post = anchor(TestAnchor::postThenComplete)
          }
        }
      }
      waitUntil(conditionDescription = "collector attached", timeoutMillis = 5_000) { subscribers.count == 1 }

      postAndAwait(post, TestSignal.Toast(1))
      waitUntil(conditionDescription = "first handler started", timeoutMillis = 5_000) { 1 in started }
      postAndAwait(post, TestSignal.Toast(2))
      // Give a restartable effect the frames it needs to react to the second signal.
      drainUiThread()
      release.complete(Unit)

      waitUntil(conditionDescription = "second handler completed", timeoutMillis = 5_000) { 2 in completed }
      drainUiThread()
      assertEquals(listOf(1, 2), started.toList())
      assertEquals(listOf(1, 2), completed.toList())
    }

  @Test
  fun `signals of other types are ignored`() =
    runComposeUiTest {
      val subscribers = SignalSubscribers()
      val toasts = CopyOnWriteArrayList<Int>()
      val others = CopyOnWriteArrayList<TestSignal.Other>()
      lateinit var post: (TestSignal, CompletableDeferred<Unit>) -> Unit
      setContent {
        RememberAnchor(scope = { testAnchor() }, customKey = "other-types") {
          CountSubscribers(subscribers) {
            HandleSignal<TestSignal.Toast> { toasts += it.n }
            HandleSignal<TestSignal.Other> { others += it }
            post = anchor(TestAnchor::postThenComplete)
          }
        }
      }
      waitUntil(conditionDescription = "collectors attached", timeoutMillis = 5_000) { subscribers.count == 2 }

      postAndAwait(post, TestSignal.Other)
      waitUntil(conditionDescription = "Other handled", timeoutMillis = 5_000) { others.isNotEmpty() }
      postAndAwait(post, TestSignal.Toast(1))
      waitUntil(conditionDescription = "Toast handled", timeoutMillis = 5_000) { toasts.isNotEmpty() }
      drainUiThread()
      assertEquals(listOf(1), toasts.toList())
      assertEquals(listOf(TestSignal.Other), others.toList())
    }

  @Test
  fun `collection stops below STARTED and resumes on restart`() =
    runComposeUiTest {
      val lifecycle = TestLifecycleOwner()
      val subscribers = SignalSubscribers()
      val received = CopyOnWriteArrayList<Int>()
      lateinit var post: (TestSignal, CompletableDeferred<Unit>) -> Unit
      setContentWithLifecycle(lifecycle) {
        RememberAnchor(scope = { testAnchor() }, customKey = "lifecycle") {
          CountSubscribers(subscribers) {
            HandleSignal<TestSignal.Toast> { received += it.n }
            post = anchor(TestAnchor::postThenComplete)
          }
        }
      }
      waitUntil(conditionDescription = "collector attached", timeoutMillis = 5_000) { subscribers.count == 1 }
      postAndAwait(post, TestSignal.Toast(1))
      waitUntil(conditionDescription = "Toast(1) handled", timeoutMillis = 5_000) { 1 in received }

      moveLifecycleTo(lifecycle, Lifecycle.State.CREATED)
      waitUntil(conditionDescription = "collector detached", timeoutMillis = 5_000) { subscribers.count == 0 }
      postAndAwait(post, TestSignal.Toast(2))

      moveLifecycleTo(lifecycle, Lifecycle.State.RESUMED)
      waitUntil(conditionDescription = "collector re-attached", timeoutMillis = 5_000) { subscribers.count == 1 }
      postAndAwait(post, TestSignal.Toast(3))
      waitUntil(conditionDescription = "Toast(3) handled", timeoutMillis = 5_000) { 3 in received }
      drainUiThread()
      // Toast(2) was posted while no collector was attached. The signal stream has no replay, so it
      // is dropped, not delivered on restart (plan 024 / #266 changes this to [1, 2, 3]).
      assertEquals(listOf(1, 3), received.toList())
    }
}
