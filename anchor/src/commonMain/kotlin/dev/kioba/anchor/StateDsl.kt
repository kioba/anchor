package dev.kioba.anchor

import kotlinx.coroutines.flow.update

@AnchorDsl
public inline fun <S> Anchor<*, S>.reduce(
  reducer: S.() -> S,
): Unit where S : ViewState =
  _viewState.update(reducer)

@AnchorDsl
public inline val <S> Anchor<*, S>.state: S where S : ViewState
  get() = _viewState.value

@AnchorDsl
public inline fun <S, R> Anchor<*, S>.withState(
  block: S.() -> R,
): R where S : ViewState =
  _viewState.value.run(block)
