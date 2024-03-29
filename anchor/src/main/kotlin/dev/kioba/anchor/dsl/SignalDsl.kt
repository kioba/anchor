package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorSignalScope


public interface AnchorSignal

public object UnitSignal : AnchorSignal


@AnchorDsl
public object SignalScope

@AnchorDsl
public suspend inline fun <E> E.post(
  block: SignalScope.() -> AnchorSignal,
): Unit where
  E : AnchorSignalScope =
  signalManager
    .signals
    .emit(SignalScope.block())
