package dev.kioba.anchor.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.kioba.anchor.compose.RenderScope
import dev.kioba.anchor.example.theme.AnchorTheme
import dev.kioba.anchor.execute

internal class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RenderScope(::MainSyntax) { state ->
        AnchorTheme {
          Scaffold(
            modifier = Modifier.fillMaxSize(),
            backgroundColor = MaterialTheme.colors.background
          ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues)) {
              Greeting(state.text)
              Button(
                onClick = execute(::clicked)
              ) {
                Text(text = "click")
              }
            }
          }
        }
      }
    }
  }
}

@Composable
internal fun Greeting(text: String) {
  Text(text = text)
}

@Preview(showBackground = true)
@Composable
internal fun DefaultPreview() {
  AnchorTheme {
    Greeting("Android")
  }
}