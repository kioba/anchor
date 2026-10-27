package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.ContainerViewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertNotNull

private sealed interface UnhandledInitEvent : Event {
  data object Setup : UnhandledInitEvent
}

/**
 * An unhandled non-cancellation failure in `init` aborts the process on
 * Kotlin/Native, so this case is pinned on desktop only. There the failure is
 * reported as uncaught and printed, and subscriptions must still start.
 */
class InitUnhandledFailureJvmTest {

  @Test
  fun `an unhandled defect in init still starts subscriptions on desktop`(): Unit =
    runBlocking {
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
      ViewModelProvider.create(owner, ContainerViewModelFactory { ContainerViewModel(anchor) })[ContainerViewModel::class]

      try {
        val reached = withTimeoutOrNull(2_000) { anchor.viewState.first { it.value == 1 } }
        assertNotNull(reached, "subscriptions never started after init failed without a defect handler")
      } finally {
        store.clear()
      }
    }
}
