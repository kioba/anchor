package dev.kioba.anchor.features.counter.model

import dev.kioba.anchor.Signal

public sealed interface CounterSignal : Signal {
  public data object Decrement : CounterSignal

  public data object Increment : CounterSignal
}
