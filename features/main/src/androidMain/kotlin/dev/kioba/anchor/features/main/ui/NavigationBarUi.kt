package dev.kioba.anchor.features.main.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.main.data.MainAnchor
import dev.kioba.anchor.features.main.data.selectCounter
import dev.kioba.anchor.features.main.data.selectHome
import dev.kioba.anchor.features.main.data.selectQuack
import dev.kioba.anchor.features.main.model.MainViewState
import dev.kioba.anchor.features.main.model.isCounterSelected
import dev.kioba.anchor.features.main.model.isHomeSelected
import dev.kioba.anchor.features.main.model.isTextInputSelected
import dev.kioba.features.resources.ResourcesR

@Composable
internal fun RowScope.QuackItem(
  state: MainViewState,
) {
  NavigationBarItem(
    selected = state.isTextInputSelected(),
    icon = { textInput() },
    onClick = anchor(MainAnchor::selectQuack),
  )
}

@Composable
internal fun RowScope.HomeItem(
  state: MainViewState,
) {
  NavigationBarItem(
    selected = state.isHomeSelected(),
    icon = { HomeIcon() },
    onClick = anchor(block = MainAnchor::selectHome),
  )
}

@Composable
internal fun RowScope.CounterItem(
  state: MainViewState,
) {
  NavigationBarItem(
    selected = state.isCounterSelected(),
    icon = { CounterIcon() },
    onClick = anchor(MainAnchor::selectCounter),
  )
}

@Composable
private fun textInput() {
  Icon(
    painter = painterResource(ResourcesR.ic_lightbulb),
    contentDescription = "text input",
  )
}

@Composable
private fun HomeIcon() {
  Icon(
    painter = painterResource(ResourcesR.ic_home),
    contentDescription = "home",
  )
}

@Composable
private fun CounterIcon() {
  Icon(
    painter = painterResource(ResourcesR.ic_plus_one),
    contentDescription = "counter",
  )
}
