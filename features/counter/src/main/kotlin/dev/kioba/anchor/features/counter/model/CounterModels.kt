package dev.kioba.anchor.features.counter.model

import androidx.compose.runtime.Immutable
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState

@Immutable
data class CounterState(
  val count: Int = 0,
) : ViewState

sealed interface CounterSignal : Signal {
  data object Decrement : CounterSignal

  data object Increment : CounterSignal
}
