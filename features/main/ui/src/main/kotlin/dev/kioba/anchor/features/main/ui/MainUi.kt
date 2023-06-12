package dev.kioba.anchor.features.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.anchor
import dev.kioba.anchor.features.main.presentation.data.clear
import dev.kioba.anchor.features.main.presentation.data.refresh
import dev.kioba.anchor.features.main.presentation.data.selectHome
import dev.kioba.anchor.features.main.presentation.data.selectProfile
import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState
import dev.kioba.anchor.features.main.ui.theme.AnchorAppTheme

@Preview
@Composable
internal fun MainUi(
  @PreviewParameter(MainPreview::class) state: MainViewState,
) {
  AnchorAppTheme {
    Scaffold(
      modifier = Modifier.fillMaxSize(),
      backgroundColor = MaterialTheme.colors.background,
      topBar = {
        TopAppBar(
          title = { Text(text = state.title) },
          elevation = 0f.dp,
        )
      },
      content = { paddingValues ->
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
          modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize(),
        ) {
          Greeting(state.details)
          AnimatedVisibility(visible = state.iterationCounter != null) {
            state.iterationCounter?.let { Greeting(it) }
          }
          RefreshButton()
          CancelButton()
        }
      },
      bottomBar = {
        BottomNavigation {
          HomeItem(state, ::selectHome)
          ProfileItem(state, ::selectProfile)
        }
      },
    )
  }
}

@Composable
private fun CancelButton() {
  Button(
    onClick = anchor(::clear)
  ) {
    Text(text = "cancel")
  }
}

@Composable
private fun RefreshButton() {
  Button(
    onClick = anchor(::refresh)
  ) {
    Text(text = "refresh")
  }
}

@Composable
private inline fun <reified E> RowScope.ProfileItem(
  state: MainViewState,
  noinline onClick: context(E) () -> Unit,
) where E : AnchorDslScope {
  BottomNavigationItem(
    selected = state.isProfileSelected(),
    icon = { ProfileIcon() },
    onClick = anchor(onClick),
  )
}

@Composable
private inline fun <reified E> RowScope.HomeItem(
  state: MainViewState,
  noinline onClick: context(E) () -> Unit,
) where E : AnchorDslScope {
  BottomNavigationItem(
    selected = state.isHomeSelected(),
    icon = { HomeIcon() },
    onClick = anchor(onClick),
  )
}

private fun MainViewState.isProfileSelected(): Boolean =
  selectedTab == MainTab.Profile

private fun MainViewState.isHomeSelected(): Boolean =
  selectedTab == MainTab.Home

@Composable
private fun ProfileIcon() {
  Icon(
    painter = painterResource(id = R.drawable.ic_person),
    contentDescription = "example navigation",
  )
}

@Composable
private fun HomeIcon() {
  Icon(
    painter = painterResource(id = R.drawable.ic_home),
    contentDescription = "home navigation",
  )
}

@Composable
private fun Greeting(text: String) {
  Text(text = text)
}
