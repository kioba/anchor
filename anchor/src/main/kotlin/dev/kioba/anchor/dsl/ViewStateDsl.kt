package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorStateScope
import kotlinx.coroutines.flow.update

@AnchorDsl
public inline val <E, S> E.state: S
  where
  E : AnchorStateScope<S>
  get() = stateManager.states.value

@AnchorDsl
public inline fun <E, S, R> E.withState(
  block: S.() -> R,
): R
  where
  E : AnchorStateScope<S> =
  stateManager.states
    .value
    .run(block)

@AnchorDsl
public inline fun <E, S> E.reduce(
  reducer: S.() -> S,
): Unit where
  E : AnchorStateScope<S> =
  stateManager.states
    .update(reducer)
