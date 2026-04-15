package dev.kioba.anchor

/**
 * A convenience typealias for [Anchor] instances that have no domain error type.
 *
 * Use this when your anchor does not raise domain-level errors.
 *
 * @param R The [Effect] type providing dependencies for side effects.
 * @param S The [ViewState] type representing the UI state.
 */
public typealias PureAnchor<R, S> = Anchor<R, S, Nothing>
