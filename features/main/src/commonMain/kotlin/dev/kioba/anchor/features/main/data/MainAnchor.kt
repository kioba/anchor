package dev.kioba.anchor.features.main.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.features.main.model.MainViewState

public typealias MainAnchor = Anchor<MainEffect, MainViewState>
public typealias MainSubScope = SubscriptionsScope<MainEffect, MainViewState>

public class MainEffect : Effect

public fun mainViewState(): MainViewState =
  MainViewState(
    title = "Anchor Example Project",
    details = "Hey",
  )

public fun RememberAnchorScope.mainAnchor(): MainAnchor =
  create(
    initialState = ::mainViewState,
    effectScope = { MainEffect() },
    init = MainAnchor::sayHi,
    subscriptions = MainSubScope::subscriptions,
  )
