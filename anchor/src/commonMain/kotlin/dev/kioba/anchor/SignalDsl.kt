package dev.kioba.anchor

@AnchorDsl
public object SignalScope

@AnchorDsl
public suspend inline fun <E> E.post(
  block: SignalScope.() -> Signal,
): Unit where E : Anchor<*, *> =
  _signals
    .emit(SignalScope.block())
