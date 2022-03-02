package dev.kioba.anchor.dsl

import dev.kioba.anchor.DslSyntax
import dev.kioba.anchor.MviDsl
import dev.kioba.anchor.MviEffect
import dev.kioba.anchor.MviScopeSyntax

@MviDsl
public object EffectScope

@MviDsl
public suspend inline fun <E> E.postEffect(
  block: EffectScope.() -> MviEffect,
): Unit where
  E : MviScopeSyntax,
  E : DslSyntax<*> =
  bridge.effectChannel
    .emit(EffectScope.block())
