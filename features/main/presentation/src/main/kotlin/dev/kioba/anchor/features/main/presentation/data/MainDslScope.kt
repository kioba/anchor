package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.dsl.AnchorEffect
import dev.kioba.anchor.dsl.anchor
import dev.kioba.anchor.dsl.effect
import dev.kioba.anchor.dsl.reduce
import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState

public typealias MainScope = AnchorScope<MainViewState>

public fun mainScope(): MainScope =
  anchorScope(
    initialState = {
      MainViewState(
        title = "Anchor Example Project",
        details = "Hey",
      )
    },
    init = sayHi(),
  )

public fun sayHi(): AnchorEffect<MainScope> =
  anchor {
    reduce { copy(details = "Hello Android!") }
  }

public fun clicked(): AnchorEffect<MainScope> =
  anchor {
    reduce { copy(details = "clicked") }
    effect { }
  }

public fun selectHome(): AnchorEffect<MainScope> =
  anchor {
    reduce {
      copy(
        selectedTab = MainTab.Home,
        details = "Hello Android!",
      )
    }
  }

public fun selectProfile(): AnchorEffect<MainScope> =
  anchor {
    reduce {
      copy(
        selectedTab = MainTab.Profile,
        details = "Examples!",
      )
    }
  }
