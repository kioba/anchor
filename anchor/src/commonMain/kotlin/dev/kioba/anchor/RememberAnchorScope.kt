package dev.kioba.anchor

public interface RememberAnchorScope {
  public fun <E, S> create(
    effectScope: () -> E,
    initialState: () -> S,
    init: (suspend Anchor<E, S>.() -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)? = null,
  ): Anchor<E, S> where E : Effect, S : ViewState
}
