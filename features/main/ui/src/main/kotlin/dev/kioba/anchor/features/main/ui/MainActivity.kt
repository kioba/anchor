package dev.kioba.anchor.features.main.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.kioba.anchor.compose.RememberAnchorScope
import dev.kioba.anchor.features.main.presentation.data.mainScope

internal class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RememberAnchorScope(::mainScope) { state ->
        MainUi(state)
      }
    }
  }
}
