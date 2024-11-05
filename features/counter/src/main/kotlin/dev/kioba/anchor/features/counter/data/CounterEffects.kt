package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.AnchorOf
import dev.kioba.anchor.anchorScope
import dev.kioba.anchor.features.counter.model.CounterSignal
import dev.kioba.anchor.post
import dev.kioba.anchor.reduce

internal fun increment(): AnchorOf<CounterAnchor> =
  anchorScope {
    reduce { copy(count = count.inc()) }
    post { CounterSignal.Increment }
  }

internal fun decrement(): AnchorOf<CounterAnchor> =
  anchorScope {
    reduce { copy(count = count.dec()) }
    post { CounterSignal.Decrement }
  }
