package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorDslScope

@AnchorDsl
public object EffectScope

@AnchorDsl
public inline fun <E, R> E.effect(
  block: EffectScope.() -> R,
): R where
  E : AnchorDslScope =
  EffectScope.block()


