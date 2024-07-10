package dev.kioba.anchor.features.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.Center
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.anchor
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.compose.RememberAnchor
import dev.kioba.anchor.reduce

private data class CounterState(
  val count: Int = 0,
)

private typealias CounterScope = AnchorScope<CounterState, Unit>

private fun counterScope(): CounterScope =
  anchorScope(initialState = ::CounterState)

context(CounterScope)
private fun increment() {
  reduce { copy(count = count.inc()) }
}

context(CounterScope)
private fun decrement() {
  reduce { copy(count = count.dec()) }
}


public class CounterActivity : ComponentActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        RememberAnchor(scope = ::counterScope) { state ->
          Scaffold { paddingValues ->
            Box(
              modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
            ) {
              Column(modifier = Modifier.align(Center)) {
                Text(
                  modifier = Modifier.Companion.align(CenterHorizontally),
                  text = state.count.toString(),
                  style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(modifier = Modifier.size(32.dp))
                Row {
                  Button(
                    onClick = anchor(::decrement)
                  ) { DecrementIcon() }
                  Spacer(modifier = Modifier.size(16.dp))
                  Button(
                    onClick = anchor(::increment),
                  ) { IncrementIcon() }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun DecrementIcon() =
  Icon(
    painter = painterResource(R.drawable.ic_remove),
    contentDescription = "Decrement",
  )

@Composable
private fun IncrementIcon() =
  Icon(
    painter = painterResource(R.drawable.ic_add),
    contentDescription = "Increment",
  )
