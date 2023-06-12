package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState

internal fun MainViewState.updateHomeSelected(): MainViewState =
  copy(
    selectedTab = MainTab.Home,
    details = "Hello Android!",
  )

internal fun MainViewState.updateProfileSelected(): MainViewState =
  copy(
    selectedTab = MainTab.Profile,
    details = "Profiles",
  )
