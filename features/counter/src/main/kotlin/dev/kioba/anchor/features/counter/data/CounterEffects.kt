package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.features.counter.model.CounterSignal

internal suspend fun CounterAnchor.increment() {
  reduce { copy(count = count.inc()) }
  post { CounterSignal.Increment }
}

internal suspend fun CounterAnchor.decrement() {
  reduce { copy(count = count.dec()) }
  post { CounterSignal.Decrement }
}
