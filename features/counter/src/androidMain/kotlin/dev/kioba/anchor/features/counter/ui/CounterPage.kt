package dev.kioba.anchor.features.counter.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import dev.kioba.anchor.compose.HandleSignal
import dev.kioba.anchor.compose.RememberAnchor
import dev.kioba.anchor.compose.anchor
import dev.kioba.anchor.features.counter.data.CounterAnchor
import dev.kioba.anchor.features.counter.data.counterAnchor
import dev.kioba.anchor.features.counter.data.decrement
import dev.kioba.anchor.features.counter.data.increment
import dev.kioba.anchor.features.counter.model.CounterSignal
import dev.kioba.features.resources.ResourcesR

@Composable
public fun CounterPage(
  paddingValues: PaddingValues,
  snackbarHostState: SnackbarHostState,
) {
  RememberAnchor(scope = { counterAnchor() }) { state ->
    HandleSignal<CounterSignal> { signal ->
      val message = when (signal) {
        CounterSignal.Decrement -> "Decremented"
        CounterSignal.Increment -> "Incremented"
      }
      snackbarHostState.showSnackbar(message = message)
    }

    Box(
      modifier = Modifier
        .padding(paddingValues)
        .fillMaxSize(),
    ) {
      Column(modifier = Modifier.align(Alignment.Center)) {
        Text(
          modifier = Modifier.align(Alignment.CenterHorizontally),
          text = state.count.toString(),
          style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(modifier = Modifier.size(32.dp))
        Row {
          Button(
            onClick = anchor(CounterAnchor::increment),
          ) { IncrementIcon() }
          Spacer(modifier = Modifier.size(16.dp))
          Button(
            onClick = anchor(CounterAnchor::decrement),
          ) { DecrementIcon() }
        }
      }
    }
  }
}

@Composable
private fun DecrementIcon() =
  Icon(
    painter = painterResource(ResourcesR.ic_remove),
    contentDescription = "Decrement",
  )

@Composable
private fun IncrementIcon() =
  Icon(
    painter = painterResource(ResourcesR.ic_add),
    contentDescription = "Increment",
  )
