package dev.kioba.anchor.features.main.presentation.model

import dev.kioba.anchor.ViewState

public data class MainViewState(
  val title: String,
  val details: String,
  val iterationCounter: String? = null,
  val selectedTab: MainTab = MainTab.Home,
) : ViewState

public sealed class MainTab {
  data object Home : MainTab()

  data object CounterTab : MainTab()

  data object QuackTab : MainTab()
}

public fun MainViewState.isQuackSelected(): Boolean =
  selectedTab is MainTab.QuackTab

public fun MainViewState.isCounterSelected(): Boolean =
  selectedTab is MainTab.CounterTab

public fun MainViewState.isHomeSelected(): Boolean =
  selectedTab is MainTab.Home
