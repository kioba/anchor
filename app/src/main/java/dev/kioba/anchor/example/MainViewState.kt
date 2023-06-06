package dev.kioba.anchor.example

internal data class MainViewState(
  val text: String,
  val selectedTab: MainTab = MainTab.HOME,
)

public enum class MainTab {
  HOME,
  EXAMPLES,
}
