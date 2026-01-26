package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime

/**
 * Scope for creating an [Anchor] instance.
 *
 * This interface is used to define how an [Anchor] should be initialized.
 */
public interface RememberAnchorScope {
  /**
   * Creates a new [Anchor] instance.
   *
   * @param E The [Effect] type.
   * @param S The [ViewState] type.
   * @param effectScope A factory function for the [Effect] dependencies.
   * @param initialState A factory function for the initial [ViewState].
   * @param init An optional initialization block executed once when the Anchor is created.
   * @param subscriptions An optional block for setting up event subscriptions.
   * @return A new [Anchor] instance.
   *
   * Example:
   * ```kotlin
   * fun RememberAnchorScope.counterAnchor(): CounterAnchor =
   *   create(
   *     initialState = { CounterState() },
   *     effectScope = { CounterEffect() },
   *     init = { /* optional initialization */ },
   *     subscriptions = { /* optional subscriptions */ }
   *   )
   * ```
   */
  public fun <E, S> create(
    effectScope: () -> E,
    initialState: () -> S,
    init: (suspend Anchor<E, S>.() -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)? = null,
  ): Anchor<E, S> where E : Effect, S : ViewState
}

/**
 * Default implementation of [RememberAnchorScope] that uses [AnchorRuntime].
 */
public object AnchorRuntimeScope : RememberAnchorScope {
  override fun <E : Effect, S : ViewState> create(
    effectScope: () -> E,
    initialState: () -> S,
    init: (suspend Anchor<E, S>.() -> Unit)?,
    subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)?,
  ): Anchor<E, S> =
    AnchorRuntime(
      initialState = initialState,
      effectScope = effectScope,
      init = init,
      subscriptions = subscriptions,
    )
}
