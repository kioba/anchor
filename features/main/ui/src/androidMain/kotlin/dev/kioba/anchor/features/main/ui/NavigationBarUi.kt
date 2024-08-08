package dev.kioba.anchor.features.main.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.main.presentation.data.selectCounter
import dev.kioba.anchor.features.main.presentation.data.selectHome
import dev.kioba.anchor.features.main.presentation.data.selectQuack
import dev.kioba.anchor.features.main.presentation.model.MainViewState
import dev.kioba.anchor.features.main.presentation.model.isCounterSelected
import dev.kioba.anchor.features.main.presentation.model.isHomeSelected
import dev.kioba.anchor.features.main.presentation.model.isQuackSelected

@Composable
internal fun RowScope.QuackItem(
  state: MainViewState,
) {
  NavigationBarItem(
    selected = state.isQuackSelected(),
    icon = { QuackIcon() },
    onClick = anchor(::selectQuack),
  )
}

@Composable
internal fun RowScope.HomeItem(
  state: MainViewState,
) {
  NavigationBarItem(
    selected = state.isHomeSelected(),
    icon = { HomeIcon() },
    onClick = anchor(::selectHome),
  )
}

@Composable
internal fun RowScope.CounterItem(
  state: MainViewState,
) {
  NavigationBarItem(
    selected = state.isCounterSelected(),
    icon = { CounterIcon() },
    onClick = anchor(::selectCounter),
  )
}

@Composable
private fun QuackIcon() {
  Icon(
    painter = painterResource(id = R.drawable.ic_lightbulb),
    contentDescription = "example",
  )
}

@Composable
private fun HomeIcon() {
  Icon(
    painter = painterResource(id = R.drawable.ic_home),
    contentDescription = "home",
  )
}

@Composable
private fun CounterIcon() {
  Icon(
    painter = painterResource(id = R.drawable.ic_plus_one),
    contentDescription = "home",
  )
}
