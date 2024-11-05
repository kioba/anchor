package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.AnchorOf
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.cancellable
import dev.kioba.anchor.effect
import dev.kioba.anchor.emit
import dev.kioba.anchor.features.main.presentation.model.MainEvent
import dev.kioba.anchor.reduce
import dev.kioba.anchor.withState
import kotlinx.coroutines.delay

public fun sayHi(): AnchorOf<MainAnchor> =
  anchorScope {
    cancellable("Initial") {
      reduce { copy(details = "Hello Android!") }
    }
  }

public fun clear(): AnchorOf<MainAnchor> =
  anchorScope {
    val value = effect { delayWithEffect() }
    emit { MainEvent.Cancel }
    reduce {
      copy(
        details = "cleared",
        iterationCounter = null,
      )
    }
  }

public fun refresh(): AnchorOf<MainAnchor> =
  anchorScope {
    emit { MainEvent.Refresh }
  }

public suspend fun MainAnchor.selectHome() {
  reduce { updateHomeSelected() }
}

public fun selectCounter(): AnchorOf<MainAnchor> =
  anchorScope {
    reduce { updateCounterSelected() }
  }

public fun selectQuack(): AnchorOf<MainAnchor> =
  anchorScope {
    reduce { updateProfileSelected() }
  }

public fun iterationCounter(
  value: Int,
): AnchorOf<MainAnchor> =
  anchorScope {
    reduce { copy(details = "refreshed") }
    reduce {
      copy(iterationCounter = value.toString())
    }
  }

public fun MainAnchor.hundreds(): Int =
  withState { hundreds }

@Suppress("UnusedReceiverParameter")
public suspend fun MainEffect.delayWithEffect(): Int =
  delay(500).let { 1 }
