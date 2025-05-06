package dev.kioba.anchor.features.config.data

import dev.kioba.anchor.features.config.model.CounterSignal

internal suspend fun CounterAnchor.increment() {
  reduce { copy(count = count.inc()) }
  post { CounterSignal.Increment }
}

internal suspend fun CounterAnchor.decrement() {
  reduce { copy(count = count.dec()) }
  post { CounterSignal.Decrement }
}
