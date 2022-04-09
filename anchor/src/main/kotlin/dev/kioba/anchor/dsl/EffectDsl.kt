package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDslSyntax
import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorCommand
import dev.kioba.anchor.AnchorSyntax

@AnchorDsl
public object CommandScope

@AnchorDsl
public suspend inline fun <E> E.postCommand(
  block: CommandScope.() -> AnchorCommand,
): Unit where
  E : AnchorSyntax,
  E : AnchorDslSyntax<*> =
  environment.effectChannel
    .emit(CommandScope.block())
