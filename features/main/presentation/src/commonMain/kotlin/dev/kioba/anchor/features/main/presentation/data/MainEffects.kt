package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.features.main.presentation.model.MainEvent
import kotlinx.coroutines.delay

public suspend fun MainAnchor.sayHi() {
  cancellable("Initial") {
    reduce { copy(details = "Hello Android!") }
  }
}

public suspend fun MainAnchor.clear() {
  val value = effect { delayWithEffect() }
  emit { MainEvent.Cancel }
  reduce {
    copy(
      details = "cleared",
      iterationCounter = null,
    )
  }
}

public suspend fun MainAnchor.refresh(): Unit =
  emit { MainEvent.Refresh }

public fun MainAnchor.selectHome() {
  reduce { updateHomeSelected() }
}

public fun MainAnchor.selectCounter(): Unit =
  reduce { updateCounterSelected() }

public fun MainAnchor.selectQuack(): Unit =
  reduce { updateProfileSelected() }

public fun MainAnchor.iterationCounter(
  value: Int,
) {
  reduce { copy(details = "refreshed") }
  reduce { copy(iterationCounter = value.toString()) }
}

public fun MainAnchor.hundreds(): Int =
  withState { hundreds }

@Suppress("UnusedReceiverParameter")
public suspend fun MainEffect.delayWithEffect(): Int =
  delay(500).let { 1 }
