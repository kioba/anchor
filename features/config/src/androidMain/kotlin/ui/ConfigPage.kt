package dev.kioba.anchor.features.counter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.compose.RememberAnchor
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.counter.data.ConfigAnchor
import dev.kioba.anchor.features.counter.data.configAnchor
import dev.kioba.anchor.features.counter.data.updateText

@Composable
public fun ConfigPage(
  paddingValues: PaddingValues,
) {
  RememberAnchor(RememberAnchorScope::configAnchor) { state ->
    Box(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize(),
    ) {
      val textFieldState: TextFieldState = rememberTextFieldState(state.text.orEmpty())
      val updateText by rememberUpdatedState(anchor(ConfigAnchor::updateText))
      LaunchedEffect(textFieldState) {
        snapshotFlow { textFieldState.text }
          .collect { text -> updateText(text.toString()) }
      }

      Column(modifier = Modifier.align(Alignment.TopCenter)) {
        OutlinedTextField(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          state = textFieldState,
        )
        Text(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          text = state.text.toString(),
          style = MaterialTheme.typography.headlineMedium,
        )
      }
    }
  }
}
