package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.Action
import dev.kioba.anchor.features.counter.model.CounterSignal
import dev.kioba.anchor.post
import dev.kioba.anchor.reduce

internal fun increment(): Action<CounterAnchor> =
  Action {
    reduce { copy(count = count.inc()) }
    post { CounterSignal.Increment }
  }

internal fun decrement(): Action<CounterAnchor> =
  Action {
    reduce { copy(count = count.dec()) }
    post { CounterSignal.Decrement }
  }
