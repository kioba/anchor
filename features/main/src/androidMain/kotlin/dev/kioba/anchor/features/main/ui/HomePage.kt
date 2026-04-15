package dev.kioba.anchor.features.main.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.main.data.MainAnchor
import dev.kioba.anchor.features.main.data.clear
import dev.kioba.anchor.features.main.data.dismissErrorDialog
import dev.kioba.anchor.features.main.data.refresh
import dev.kioba.anchor.features.main.data.triggerLocalError
import dev.kioba.anchor.features.main.data.triggerPropagatedError
import dev.kioba.anchor.features.main.model.MainViewState

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
    LocalErrorButton()
    PropagatedErrorButton()
    ErrorDialog(state)
  }
}

@Composable
private fun CancelButton() {
  Button(
    onClick = anchor(MainAnchor::clear),
  ) {
    Text(text = "cancel")
  }
}

@Composable
private fun RefreshButton() {
  Button(
    onClick = anchor(MainAnchor::refresh),
  ) {
    Text(text = "refresh")
  }
}

@Composable
private fun LocalErrorButton() {
  Button(
    onClick = anchor(MainAnchor::triggerLocalError),
  ) {
    Text(text = "local error")
  }
}

@Composable
private fun PropagatedErrorButton() {
  Button(
    onClick = anchor(MainAnchor::triggerPropagatedError),
  ) {
    Text(text = "propagated error")
  }
}

@Composable
private fun ErrorDialog(state: MainViewState) {
  val errorMessage = state.errorDialog ?: return
  AlertDialog(
    onDismissRequest = anchor(MainAnchor::dismissErrorDialog),
    title = { Text(text = "Error") },
    text = { Text(text = errorMessage) },
    confirmButton = {
      Button(onClick = anchor(MainAnchor::dismissErrorDialog)) {
        Text(text = "OK")
      }
    },
  )
}
