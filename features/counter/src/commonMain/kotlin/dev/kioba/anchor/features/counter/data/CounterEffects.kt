package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.features.counter.model.CounterSignal

public suspend fun CounterAnchor.increment() {
  reduce { copy(count = count.inc()) }
  post { CounterSignal.Increment }
}

public suspend fun CounterAnchor.decrement() {
  reduce { copy(count = count.dec()) }
  post { CounterSignal.Decrement }
}
