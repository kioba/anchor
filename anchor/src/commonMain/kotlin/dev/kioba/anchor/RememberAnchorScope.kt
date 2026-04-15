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
   * @param R The [Effect] type.
   * @param S The [ViewState] type.
   * @param Err The domain error type. Use [Nothing] when no domain errors are needed.
   * @param effectScope A factory function for the [Effect] dependencies.
   * @param initialState A factory function for the initial [ViewState].
   * @param init An optional initialization block executed once when the Anchor is created.
   * @param subscriptions An optional block for setting up event subscriptions.
   * @param onDomainError An optional callback invoked when a domain error is raised.
   * @param defect An optional callback invoked when an unexpected error occurs.
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
  public fun <R, S, Err> create(
    effectScope: () -> R,
    initialState: () -> S,
    init: (suspend Anchor<R, S, Err>.() -> Unit)? = null,
    onDomainError: (suspend Anchor<R, S, Err>.(Err) -> Unit)? = null,
    defect: (suspend Anchor<R, S, Err>.(Throwable) -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)? = null,
  ): Anchor<R, S, Err> where R : Effect, S : ViewState, Err : Any
}

/**
 * Default implementation of [RememberAnchorScope] that uses [AnchorRuntime].
 */
internal object AnchorRuntimeScope : RememberAnchorScope {
  override fun <R : Effect, S : ViewState, Err : Any> create(
    effectScope: () -> R,
    initialState: () -> S,
    init: (suspend Anchor<R, S, Err>.() -> Unit)?,
    onDomainError: (suspend Anchor<R, S, Err>.(Err) -> Unit)?,
    defect: (suspend Anchor<R, S, Err>.(Throwable) -> Unit)?,
    subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)?,
  ): Anchor<R, S, Err> =
    AnchorRuntime(
      initialState = initialState,
      effectScope = effectScope,
      init = init,
      subscriptions = subscriptions,
      onDomainError = onDomainError,
      defect = defect,
    )
}
