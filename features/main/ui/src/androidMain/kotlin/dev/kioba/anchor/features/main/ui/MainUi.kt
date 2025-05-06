package dev.kioba.anchor.features.main.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.features.config.ui.ConfigPage
import dev.kioba.anchor.features.config.ui.CounterPage
import dev.kioba.anchor.features.main.presentation.model.MainTab
import dev.kioba.anchor.features.main.presentation.model.MainViewState

private const val mainContentAnimationLabel = "MainSelectedTabAnimation"

@OptIn(ExperimentalMaterial3Api::class)
@Preview
@Composable
public fun ViewModelStoreOwner.MainUi(
  @PreviewParameter(MainPreview::class) state: MainViewState,
) {
  val snackbarHostState = remember { SnackbarHostState() }

  AnchorTheme {
    Scaffold(
      topBar = { TopAppBar(title = { Text(text = state.title) }) },
      snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
      content = { paddingValues ->
        AnimatedContent(
          targetState = state.selectedTab,
          label = mainContentAnimationLabel,
        ) { targetState ->
          when (targetState) {
            MainTab.Home -> HomePage(paddingValues, state)
            MainTab.CounterTab -> CounterPage(paddingValues, snackbarHostState)
            MainTab.ConfigTab -> ConfigPage(paddingValues)
          }
        }
      },
      bottomBar = {
        NavigationBar {
          HomeItem(state = state)
          CounterItem(state = state)
          QuackItem(state = state)
        }
      },
    )
  }
}
