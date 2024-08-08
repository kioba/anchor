package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.features.counter.model.CounterState

internal typealias CounterAnchor = Anchor<EmptyEffect, CounterState>

internal fun counterAnchor(): CounterAnchor =
  CounterAnchor(
    initialState = ::CounterState,
    effectScope = { EmptyEffect },
  )
