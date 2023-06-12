package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.dsl.SubscriptionsScope
import dev.kioba.anchor.features.main.presentation.model.MainViewState

public typealias MainScope = AnchorScope<MainViewState, MainEffects>
public typealias MainSubScope = SubscriptionsScope<MainViewState, MainEffects>

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
    effectScope = { MainEffects },
    init = ::sayHi,
    subscriptions = ::subscriptions,
  )
