package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.features.main.presentation.model.MainViewState

public typealias MainAnchor = Anchor<MainEffect, MainViewState>
public typealias MainSubScope = SubscriptionsScope<MainEffect, MainViewState>

public object MainEffect : Effect

internal fun mainViewState() =
  MainViewState(
    title = "Anchor Example Project",
    details = "Hey",
  )

public fun mainAnchor(): MainAnchor =
  Anchor(
    initialState = ::mainViewState,
    effectScope = { MainEffect },
    init = ::sayHi,
    subscriptions = MainSubScope::subscriptions,
  )
