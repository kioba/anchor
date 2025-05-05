package dev.kioba.anchor.features.config.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelStoreOwner
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.compose.RememberAnchor
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.config.data.ConfigAnchor
import dev.kioba.anchor.features.config.data.configAnchor
import dev.kioba.anchor.features.config.data.updateText

@Composable
public fun ViewModelStoreOwner.ConfigPage(
  paddingValues: PaddingValues,
) {
  RememberAnchor(RememberAnchorScope::configAnchor) { state ->
    Box(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize(),
    ) {
      val text: TextFieldState = rememberTextFieldState(state.text.orEmpty())
      text.edit {
        anchor(ConfigAnchor::updateText)(originalText.toString())
      }
      Column(modifier = Modifier.align(Alignment.Center)) {
        OutlinedTextField(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          state = text,
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
