package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorScope


public interface AnchorSignal

public object UnitSignal : AnchorSignal


@AnchorDsl
public object SignalScope

@AnchorDsl
public suspend inline fun <E> E.post(
  block: SignalScope.() -> AnchorSignal,
): Unit where
  E : AnchorScope<*> =
  signalManager
    .signals
    .emit(SignalScope.block())
