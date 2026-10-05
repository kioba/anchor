package dev.kioba.anchor.compose

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState

// Shared fixtures for the anchor-compose UI tests. Keep them generic: RememberAnchor, anchor(),
// collectState and HandleSignal tests all build on this one anchor.

data class TestState(
  val count: Int = 0,
  val label: String = "",
) : ViewState

sealed interface TestSignal : Signal {
  data class Toast(
    val n: Int,
  ) : TestSignal

  data object Other : TestSignal
}

typealias TestAnchor = Anchor<EmptyEffect, TestState, Nothing>

fun RememberAnchorScope.testAnchor(): TestAnchor =
  create(
    initialState = ::TestState,
    effectScope = { EmptyEffect },
  )

suspend fun TestAnchor.increment() {
  reduce { copy(count = count + 1) }
}

suspend fun TestAnchor.setLabel(label: String) {
  reduce { copy(label = label) }
}

suspend fun TestAnchor.postToast(n: Int) {
  post { TestSignal.Toast(n) }
}

// Posts every signal from one action, so they enter the signal stream back to back and in order.
// Separate anchor() calls each launch their own coroutine and may interleave.
suspend fun TestAnchor.postSignals(signals: List<TestSignal>) {
  signals.forEach { signal -> post { signal } }
}
