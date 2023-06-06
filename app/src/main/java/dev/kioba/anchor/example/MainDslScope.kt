package dev.kioba.anchor.example

import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.dsl.AnchorEffect
import dev.kioba.anchor.dsl.anchor
import dev.kioba.anchor.dsl.effect
import dev.kioba.anchor.dsl.reduce

internal typealias MainScope = AnchorScope<MainViewState>

internal fun mainScope(): MainScope =
  anchorScope(
    initialState = { MainViewState(text = "Hey") },
    init = sayHi(),
  )

internal fun sayHi(): AnchorEffect<MainScope> =
  anchor {
    reduce { copy(text = "Hello Android!") }
  }

internal fun clicked(): AnchorEffect<MainScope> =
  anchor {
    reduce { copy(text = "clicked") }
    effect {  }
  }

internal fun selectHome(): AnchorEffect<MainScope> =
  anchor {
    reduce {
      copy(
        selectedTab = MainTab.HOME,
        text = "Hello Android!",
      )
    }
  }

internal fun selectExamples(): AnchorEffect<MainScope> =
  anchor {
    reduce {
      copy(
        selectedTab = MainTab.EXAMPLES,
        text = "Examples!",
      )
    }
  }
