package dev.kioba.anchor.dsl

import dev.kioba.anchor.DslSyntax
import dev.kioba.anchor.MviDsl
import dev.kioba.anchor.MviScopeSyntax
import kotlinx.coroutines.flow.update

@MviDsl
public inline val <E, S> E.state: S
  where
  E : MviScopeSyntax,
  E : DslSyntax<S>
  get() = bridge.stateChannel.value

@MviDsl
public inline fun <E, S, R> E.state(
  block: S.() -> R,
): R
  where
  E : MviScopeSyntax,
  E : DslSyntax<S> =
  bridge.stateChannel
    .value
    .run(block)

@MviDsl
public inline fun <E, S> E.reduce(
  reducer: S.() -> S,
): Unit where
  E : MviScopeSyntax,
  E : DslSyntax<S> =
  bridge.stateChannel
    .update(reducer)
