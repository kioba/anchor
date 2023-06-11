package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorEffectScope

@AnchorDsl
public object EffectScope

@AnchorDsl
public inline fun <A, E, R> A.effect(
  block: E.() -> R,
): R where
  A : AnchorEffectScope<E> =
//  with(EffectScope) {
    block(effectManager.effectScope)
//  }


