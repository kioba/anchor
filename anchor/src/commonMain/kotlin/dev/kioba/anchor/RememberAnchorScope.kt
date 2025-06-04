package dev.kioba.anchor

public interface RememberAnchorScope {
  public fun <E, S> create(
    effectScope: () -> E,
    initialState: () -> S,
    init: (suspend Anchor<E, S>.() -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)? = null,
  ): Anchor<E, S> where E : Effect, S : ViewState
}

public object AnchorRuntimeScope : RememberAnchorScope {
  override fun <E : Effect, S : ViewState> create(
    effectScope: () -> E,
    initialState: () -> S,
    init: (suspend Anchor<E, S>.() -> Unit)?,
    subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)?
  ): Anchor<E, S> =
    AnchorRuntime(
      initialStateBuilder = initialState,
      effectBuilder = effectScope,
      init = init,
      subscriptions = subscriptions,
    )
}
