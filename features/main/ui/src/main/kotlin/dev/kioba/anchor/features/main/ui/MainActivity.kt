package dev.kioba.anchor.features.main.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.kioba.anchor.compose.RememberAnchor
import dev.kioba.anchor.features.main.presentation.data.mainScope

internal class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RememberAnchor(::mainScope) { state ->
        MainUi(state)
      }
    }
  }
}
