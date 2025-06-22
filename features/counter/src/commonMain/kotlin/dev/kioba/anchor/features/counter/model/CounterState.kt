package dev.kioba.anchor.features.counter.model

import androidx.compose.runtime.Immutable
import dev.kioba.anchor.ViewState

@Immutable
 public data class CounterState(
  val count: Int = 0,
 ) : ViewState
