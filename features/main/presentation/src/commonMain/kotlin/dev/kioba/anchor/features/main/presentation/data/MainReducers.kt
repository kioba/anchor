package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState

internal fun MainViewState.updateHomeSelected(): MainViewState =
  copy(
    selectedTab = MainTab.Home,
    details = "Hello Android!",
  )

internal fun MainViewState.updateCounterSelected(): MainViewState =
  copy(
    selectedTab = MainTab.CounterTab,
  )

internal fun MainViewState.updateProfileSelected(): MainViewState =
  copy(
    selectedTab = MainTab.ConfigTab,
    details = "Profiles",
  )
