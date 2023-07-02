package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorEffectScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@AnchorDsl
public object EffectScope

@AnchorDsl
public suspend inline fun <A, E, R> A.effect(
  coroutineContext: CoroutineContext = Dispatchers.IO,
  crossinline block: suspend E.() -> R,
): R where
  A : AnchorEffectScope<E> =
  withContext(coroutineContext) {
    block(effectManager.effectScope)
  }

