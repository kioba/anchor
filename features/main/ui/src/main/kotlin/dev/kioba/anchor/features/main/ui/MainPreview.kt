package dev.kioba.anchor.features.main.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState

internal class MainPreview : PreviewParameterProvider<MainViewState> {
  override val values: Sequence<MainViewState> = sequenceOf(defaultState())

  private fun defaultState(): MainViewState =
    MainViewState(
      selectedTab = MainTab.Home,
      title = "Anchor Example Project",
      details = "Hello Preview",
    )
}