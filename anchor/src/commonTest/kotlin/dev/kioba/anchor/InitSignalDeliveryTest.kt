package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.ContainerViewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class InitSignalDeliveryTest {
  private data object Loaded : Signal

  private data class Failed(
    val message: String?,
  ) : Signal

  /**
   * Starts [runtime] in a [ContainerViewModel], the way `RememberAnchor` and
   * iOS `rememberAnchor` do, runs [block], and clears the ViewModel afterwards.
   */
  private suspend fun inViewModel(
    runtime: AnchorRuntime<EmptyEffect, TestState, TestError>,
    block: suspend (ContainerViewModel<EmptyEffect, TestState, TestError>) -> Unit,
  ) {
    val store = ViewModelStore()
    val owner =
      object : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = store
      }
    val provider = ViewModelProvider.create(owner, ContainerViewModelFactory { ContainerViewModel(runtime) })
    @Suppress("UNCHECKED_CAST")
    val viewModel = provider[ContainerViewModel::class] as ContainerViewModel<EmptyEffect, TestState, TestError>
    try {
      block(viewModel)
    } finally {
      store.clear()
    }
  }

  @Test
  fun `signal posted from init is delivered to a collector that subscribes afterwards`(): Unit =
    runBlocking {
      val anchor =
        AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
          init = { post { Loaded } },
        )

      // ContainerViewModel does this in its own init block, on Dispatchers.Default,
      // before Compose has had a chance to run HandleSignal's collector.
      anchor.consumeInitial()

      // HandleSignal / collectSignals attach here — after init has completed.
      val received = withTimeoutOrNull(500) { anchor.signals.first().provide() }

      assertNotNull(received, "Signal posted from init was dropped: no subscriber, replay = 0")
    }

  @Test
  fun `signal posted from init reaches a collector attaching one frame later via ContainerViewModel`(): Unit =
    runBlocking {
      val runtime =
        AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
          init = { post { Loaded } },
        )

      inViewModel(runtime) { vm ->
        delay(16)
        val received = withTimeoutOrNull(500) { vm.signals.first().provide() }
        assertEquals(Loaded, received)

        // Delivered once: the next collector (after a rotation, say) does not see it again.
        val replayed = withTimeoutOrNull(100) { vm.signals.first().provide() }
        assertNull(replayed, "a delivered init signal was replayed to the next collector")
      }
    }

  @Test
  fun `signal a defect handler posts for an init failure reaches a later collector`(): Unit =
    runBlocking {
      // The ErrorScope KDoc pattern: report a failure from init as a signal.
      val runtime =
        AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
          init = { error("init failed") },
          defect = { throwable -> post { Failed(throwable.message) } },
        )

      inViewModel(runtime) { vm ->
        delay(16)
        val received = withTimeoutOrNull(500) { vm.signals.first().provide() }
        assertEquals(Failed("init failed"), received)
      }
    }
}
