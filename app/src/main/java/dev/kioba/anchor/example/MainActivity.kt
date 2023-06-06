package dev.kioba.anchor.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.Dp
import dev.kioba.anchor.compose.RememberAnchorScope
import dev.kioba.anchor.example.theme.AnchorAppTheme
import dev.kioba.anchor.execute

internal class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      RememberAnchorScope(::mainScope) { state ->
        AnchorAppTheme {
          Scaffold(
            bottomBar = {
              BottomNavigation(elevation = Dp(0f)) {
                BottomNavigationItem(
                  selected = state.selectedTab == MainTab.HOME,
                  icon = {
                    Icon(
                      painter = painterResource(id = R.drawable.ic_home),
                      contentDescription = "home navigation",
                    )
                  },
                  onClick = execute { selectHome() },
                )
                BottomNavigationItem(
                  selected = state.selectedTab == MainTab.EXAMPLES,
                  icon = {
                    Icon(
                      painter = painterResource(id = R.drawable.ic_home),
                      contentDescription = "example navigation",
                    )
                  },
                  onClick = execute { selectExamples() }
                )
              }
            },
            topBar = {
              TopAppBar(
                title = { Text(text = "Anchor Example") },
                elevation = Dp(0f),
              )
            },
            modifier = Modifier.fillMaxSize(),
            backgroundColor = MaterialTheme.colors.background
          ) { paddingValues ->
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            ) {
              Greeting(state.text)
              Button(
                onClick = execute { clicked() }
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
  AnchorAppTheme {
    Greeting("Android")
  }
}