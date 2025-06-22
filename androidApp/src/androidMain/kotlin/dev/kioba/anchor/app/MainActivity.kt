package dev.kioba.anchor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.compose.RememberAnchor
import dev.kioba.anchor.features.main.data.mainAnchor
import dev.kioba.anchor.features.main.ui.MainUi

internal class MainActivity : ComponentActivity() {
  override fun onCreate(
    savedInstanceState: Bundle?,
  ) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)
    setContent {
      RememberAnchor(RememberAnchorScope::mainAnchor) { state ->
        MainUi(state)
      }
    }
  }
}
