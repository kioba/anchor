package dev.kioba.anchor.features.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.main.presentation.data.clear
import dev.kioba.anchor.features.main.presentation.data.refresh
import dev.kioba.anchor.features.main.presentation.model.MainViewState

@Composable
internal fun HomePage(
  paddingValues: PaddingValues,
  state: MainViewState,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier =
      Modifier
        .padding(paddingValues)
        .fillMaxSize(),
  ) {
    Text(
      text = state.details,
      modifier = Modifier.padding(16.dp),
    )
    AnimatedVisibility(visible = state.iterationCounter != null) {
      state.iterationCounter?.let { Text(it) }
    }
    RefreshButton()
    CancelButton()
  }
}

@Composable
private fun CancelButton() {
  Button(
    onClick = anchor(::clear),
  ) {
    Text(text = "cancel")
  }
}

@Composable
private fun RefreshButton() {
  Button(
    onClick = anchor(::refresh),
  ) {
    Text(text = "refresh")
  }
}
