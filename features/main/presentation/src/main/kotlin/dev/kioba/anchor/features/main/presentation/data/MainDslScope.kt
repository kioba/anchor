package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.dsl.Anchor
import dev.kioba.anchor.dsl.anchor
import dev.kioba.anchor.dsl.effect
import dev.kioba.anchor.dsl.reduce
import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState

public typealias MainScope = AnchorScope<MainViewState, MainEffects>

public object MainEffects

context(MainEffects)
  public fun hello(): Int = 1

public fun mainScope(): MainScope =
  anchorScope(
    initialState = {
      MainViewState(
        title = "Anchor Example Project",
        details = "Hey",
      )
    },
    effects = { MainEffects },
    init = ::sayHi,
  )

public fun sayHi(): Anchor<MainScope> =
  anchor("Initial") {
    reduce { copy(details = "Hello Android!") }
  }

public fun clicked(): Anchor<MainScope> =
  anchor {
    reduce { copy(details = "clicked") }
    val value = effect { hello() }
  }

public fun selectHome(): Anchor<MainScope> =
  anchor {
    reduce {
      copy(
        selectedTab = MainTab.Home,
        details = "Hello Android!",
      )
    }
  }

public fun selectProfile(): Anchor<MainScope> =
  anchor {
    reduce {
      copy(
        selectedTab = MainTab.Profile,
        details = "Examples!",
      )
    }
  }
