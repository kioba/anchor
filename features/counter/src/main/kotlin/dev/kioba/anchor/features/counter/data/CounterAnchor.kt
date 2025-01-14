package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState

internal typealias CounterEffect = EmptyEffect
internal typealias CounterAnchor = Anchor<CounterEffect, CounterState>

public data class CounterState(
  val count: Int = 0,
) : ViewState

internal fun RememberAnchorScope.counterAnchor(): CounterAnchor =
  create(
    initialState = ::CounterState,
    effectScope = { CounterEffect },
  )
