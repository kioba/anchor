package dev.kioba.anchor.features.counter.data

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState

public class CounterEffect: Effect
public abstract class CounterAnchorType: CounterAnchor()
public typealias CounterAnchor = Anchor<CounterEffect, CounterState>

public data class CounterState(
  val count: Int = 0,
) : ViewState

public fun RememberAnchorScope.counterAnchor(): CounterAnchor =
  create(
    initialState = ::CounterState,
    effectScope = { CounterEffect() },
  )
