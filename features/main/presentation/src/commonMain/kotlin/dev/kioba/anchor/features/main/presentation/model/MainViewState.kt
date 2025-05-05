package dev.kioba.anchor.features.main.presentation.model

import dev.kioba.anchor.ViewState

public data class MainViewState(
  val title: String,
  val details: String,
  val hundreds: Int = 0,
  val iterationCounter: String? = null,
  val selectedTab: MainTab = MainTab.Home,
) : ViewState

public sealed class MainTab {
  public data object Home : MainTab()

  public data object CounterTab : MainTab()

  public data object ConfigTab : MainTab()
}

public fun MainViewState.isQuackSelected(): Boolean =
  selectedTab is MainTab.ConfigTab

public fun MainViewState.isCounterSelected(): Boolean =
  selectedTab is MainTab.CounterTab

public fun MainViewState.isHomeSelected(): Boolean =
  selectedTab is MainTab.Home
