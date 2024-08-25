package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.features.main.presentation.model.MainViewState
import kotlinx.coroutines.delay

public typealias MainAnchor = Anchor<MainEffects, MainViewState>
public typealias MainSubScope = SubscriptionsScope<MainEffects, MainViewState>

public object MainEffects : Effect

@Suppress("UnusedReceiverParameter")
public suspend fun MainEffects.hello() =
  delay(100).let { 1 }

public fun mainScope(): MainAnchor =
  Anchor(
    initialState = {
      MainViewState(
        title = "Anchor Example Project",
        details = "Hey",
      )
    },
    effectScope = { MainEffects },
    init = ::sayHi,
    subscriptions = MainSubScope::subscriptions,
  )
