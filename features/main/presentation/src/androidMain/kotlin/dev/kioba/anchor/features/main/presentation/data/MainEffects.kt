package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.Action
import dev.kioba.anchor.cancellable
import dev.kioba.anchor.effect
import dev.kioba.anchor.emit
import dev.kioba.anchor.features.main.presentation.model.MainEvent
import dev.kioba.anchor.reduce

public fun sayHi(): Action<MainAnchor> =
  Action {
    cancellable("Initial") {
      reduce { copy(details = "Hello Android!") }
    }
  }

public fun clear(): Action<MainAnchor> =
  Action {
    val value = effect { hello() }
    emit { MainEvent.Cancel }
    reduce {
      copy(
        details = "cleared",
        iterationCounter = null,
      )
    }
  }

public fun refresh(): Action<MainAnchor> =
  Action {
    emit { MainEvent.Refresh }
  }

public fun selectHome(): Action<MainAnchor> =
  Action {
    reduce { updateHomeSelected() }
  }

public fun selectCounter(): Action<MainAnchor> =
  Action {
    reduce { updateCounterSelected() }
  }

public fun selectQuack(): Action<MainAnchor> =
  Action {
    reduce { updateProfileSelected() }
  }

public fun iterationCounter(
  value: Int,
): Action<MainAnchor> =
  Action {
    reduce { copy(details = "refreshed") }
    reduce { copy(iterationCounter = value.toString()) }
  }
