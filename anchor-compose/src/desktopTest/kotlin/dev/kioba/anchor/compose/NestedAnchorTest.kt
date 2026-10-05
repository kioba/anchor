package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

data class OuterState(
  val n: Int = 0,
) : ViewState

data class InnerState(
  val s: String = "",
) : ViewState

typealias OuterAnchor = Anchor<EmptyEffect, OuterState, Nothing>

typealias InnerAnchor = Anchor<EmptyEffect, InnerState, Nothing>

data object GoHome : Signal

suspend fun OuterAnchor.bump() {
  reduce { copy(n = n + 1) }
}

suspend fun OuterAnchor.goHome() {
  post { GoHome }
}

suspend fun InnerAnchor.rename(s: String) {
  reduce { copy(s = s) }
}

class NestedProbe {
  val defect = AtomicReference<Throwable?>(null)
  val outerN = AtomicInteger(0)
  val innerS = AtomicReference("")
  val outerHandled = AtomicInteger(0)
  val innerHandled = AtomicInteger(0)
  var bump: (() -> Unit)? = null
  var goHome: (() -> Unit)? = null
  var rename: ((String) -> Unit)? = null
}

@Composable
fun NestedHarness(
  probe: NestedProbe,
  key: String,
) {
  RememberAnchor(
    scope = {
      create<EmptyEffect, OuterState, Nothing>(
        effectScope = { EmptyEffect },
        initialState = ::OuterState,
        defect = { probe.defect.set(it) },
      )
    },
    customKey = "$key-outer",
  ) {
    probe.outerN.set(state.n)
    HandleSignal<GoHome> { probe.outerHandled.incrementAndGet() }
    RememberAnchor(
      scope = {
        create<EmptyEffect, InnerState, Nothing>(
          effectScope = { EmptyEffect },
          initialState = ::InnerState,
          defect = { probe.defect.set(it) },
        )
      },
      customKey = "$key-inner",
    ) {
      probe.innerS.set(state.s)
      HandleSignal<GoHome> { probe.innerHandled.incrementAndGet() }
      probe.bump = anchor(OuterAnchor::bump)
      probe.goHome = anchor(OuterAnchor::goHome)
      probe.rename = anchor(InnerAnchor::rename)
    }
  }
}

@OptIn(ExperimentalTestApi::class)
class NestedAnchorTest {
  @Test
  fun `outer reduce action called inside a nested RememberAnchor updates the outer anchor`() =
    runComposeUiTest {
      val probe = NestedProbe()
      setContent { NestedHarness(probe, "reduce") }
      waitForIdle()
      probe.bump!!()
      waitUntil(timeoutMillis = 5_000) { probe.outerN.get() == 1 || probe.defect.get() != null }
      assertNull(probe.defect.get(), "outer action ran on the inner anchor")
      assertEquals(1, probe.outerN.get())
    }

  @Test
  fun `outer signal action called inside a nested RememberAnchor posts on the outer anchor`() =
    runComposeUiTest {
      val probe = NestedProbe()
      setContent { NestedHarness(probe, "signal") }
      waitForIdle()
      probe.goHome!!()
      waitUntil(timeoutMillis = 5_000) { probe.outerHandled.get() + probe.innerHandled.get() > 0 }
      waitForIdle()
      assertEquals(1, probe.outerHandled.get(), "signal not delivered to the outer anchor")
      assertEquals(0, probe.innerHandled.get(), "signal misrouted to the inner anchor")
    }

  @Test
  fun `inner action still reaches the inner anchor`() =
    runComposeUiTest {
      val probe = NestedProbe()
      setContent { NestedHarness(probe, "inner") }
      waitForIdle()
      probe.rename!!("x")
      waitUntil(timeoutMillis = 5_000) { probe.innerS.get() == "x" }
      assertNull(probe.defect.get())
    }

  @Test
  fun `anchor outside any RememberAnchor fails naming the missing ViewState`() {
    val failure =
      assertFailsWith<IllegalStateException> {
        runComposeUiTest {
          setContent { anchor(OuterAnchor::bump) }
          waitForIdle()
        }
      }
    assertContains(failure.message.orEmpty(), "OuterState")
  }

  @Test
  fun `anchor under PreviewAnchor is a no-op`() =
    runComposeUiTest {
      var bump: (() -> Unit)? = null
      setContent { PreviewAnchor(OuterState()) { bump = anchor(OuterAnchor::bump) } }
      waitForIdle()
      bump!!()
      waitForIdle()
    }

  @Test
  fun `anchor returns the same callback across recompositions`() =
    runComposeUiTest {
      val callbacks = mutableListOf<Pair<Int, () -> Unit>>()
      var tick by mutableIntStateOf(0)
      setContent {
        RememberAnchor(
          scope = {
            create<EmptyEffect, OuterState, Nothing>(
              effectScope = { EmptyEffect },
              initialState = ::OuterState,
            )
          },
          customKey = "stable-outer",
        ) {
          callbacks += tick to anchor(OuterAnchor::bump)
        }
      }
      waitForIdle()
      tick++
      waitForIdle()
      assertEquals(listOf(0, 1), callbacks.map { it.first }.distinct())
      assertSame(callbacks.first().second, callbacks.last().second)
    }
}
