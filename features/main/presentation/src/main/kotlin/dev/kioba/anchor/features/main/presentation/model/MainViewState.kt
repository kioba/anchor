package dev.kioba.anchor.features.main.presentation.model

public data class MainViewState(
  val title: String,
  val details: String,
  val iterationCounter: String? = null,
  val selectedTab: MainTab = MainTab.Home,
)

public enum class MainTab {
  Home,
  Profile,
}
