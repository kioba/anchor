package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.ViewState

internal typealias CounterEffect = EmptyEffect
internal typealias CounterAnchor = Anchor<CounterEffect, CounterState>

internal fun counterAnchor(
  effect: CounterEffect = CounterEffect,
): CounterAnchor =
  CounterAnchor(
    initialState = ::CounterState,
    effectScope = { effect },
  )

public data class CounterState(
  val count: Int = 0,
) : ViewState
