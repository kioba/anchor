package dev.kioba.anchor.features.config.model

import dev.kioba.anchor.Signal

internal sealed interface CounterSignal : Signal {
  data object Decrement : CounterSignal

  data object Increment : CounterSignal
}
