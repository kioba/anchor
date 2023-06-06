package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorScope
import kotlinx.coroutines.flow.update

@AnchorDsl
public inline val <E, S> E.state: S
  where
  E : AnchorScope<S>
  get() = stateManager.states.value

@AnchorDsl
public inline fun <E, S, R> E.withState(
  block: S.() -> R,
): R
  where
  E : AnchorScope<S> =
  stateManager.states
    .value
    .run(block)

@AnchorDsl
public inline fun <E, S> E.reduce(
  reducer: S.() -> S,
): Unit where
  E : AnchorScope<S> =
  stateManager.states
    .update(reducer)
