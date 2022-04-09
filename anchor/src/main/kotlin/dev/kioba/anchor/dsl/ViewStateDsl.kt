package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDslSyntax
import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorSyntax
import kotlinx.coroutines.flow.update

@AnchorDsl
public inline val <E, S> E.state: S
  where
  E : AnchorSyntax,
  E : AnchorDslSyntax<S>
  get() = environment.stateChannel.value

@AnchorDsl
public inline fun <E, S, R> E.state(
  block: S.() -> R,
): R
  where
  E : AnchorSyntax,
  E : AnchorDslSyntax<S> =
  environment.stateChannel
    .value
    .run(block)

@AnchorDsl
public inline fun <E, S> E.reduce(
  reducer: S.() -> S,
): Unit where
  E : AnchorSyntax,
  E : AnchorDslSyntax<S> =
  environment.stateChannel
    .update(reducer)
