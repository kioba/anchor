package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.ContainerViewModelFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private sealed interface UnhandledInitEvent : Event {
  data object Setup : UnhandledInitEvent
}

/**
 * An unhandled non-cancellation failure in `init` aborts the process on
 * Kotlin/Native, so this case is pinned on desktop only. There the failure
 * is reported as uncaught, which this test captures, and subscriptions must
 * still start.
 */
class InitUnhandledFailureJvmTest {

  @Test
  fun `an unhandled defect in init still starts subscriptions on desktop`(): Unit =
    runBlocking {
      val previous = Thread.getDefaultUncaughtExceptionHandler()
      val reported = CompletableDeferred<Throwable>()
      Thread.setDefaultUncaughtExceptionHandler { _, throwable -> reported.complete(throwable) }
      val anchor =
        AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
          init = {
            emit { UnhandledInitEvent.Setup }
            throw IllegalStateException("init boom")
          },
          subscriptions = {
            connect<UnhandledInitEvent.Setup> { events -> events.anchor { reduce { copy(value = 1) } } }
          },
        )
      val store = ViewModelStore()
      val owner =
        object : ViewModelStoreOwner {
          override val viewModelStore: ViewModelStore = store
        }
      val provider = ViewModelProvider.create(owner, ContainerViewModelFactory { ContainerViewModel(anchor) })
      provider[ContainerViewModel::class]

      try {
        val reached = withTimeoutOrNull(2_000) { anchor.viewState.first { it.value == 1 } }
        assertNotNull(reached, "subscriptions never started after init failed without a defect handler")
        val throwable = withTimeoutOrNull(2_000) { reported.await() }
        assertNotNull(throwable, "the init failure was never reported as uncaught")
        assertEquals("init boom", throwable.message)
      } finally {
        store.clear()
        Thread.setDefaultUncaughtExceptionHandler(previous)
      }
    }
}
