package dev.kioba.anchor

/**
 * Provides safe access to execute actions on an Anchor instance.
 *
 * This interface is typically used internally by the [anchor] composable functions to
 * execute user-defined actions within a [RememberAnchor] scope. Most users will not
 * interact with this interface directly, but instead use the [anchor] helper functions.
 *
 * As a fun interface, implementations can be created using lambda syntax, enabling
 * clean, concise code:
 * ```kotlin
 * val scope = AnchorScope<MyEffect, MyState> { block ->
 *   myAnchor.block()
 * }
 * ```
 *
 * @param E The Effect type providing dependencies for side effects
 * @param S The ViewState type representing the UI state
 *
 * @see dev.kioba.anchor.compose.anchor For the primary way to execute actions
 * @see dev.kioba.anchor.compose.RememberAnchor For setting up the Anchor scope
 */
public fun interface AnchorScope<out E : Effect, out S : ViewState> {
  /**
   * Executes an action on the underlying Anchor instance.
   *
   * This is called internally when UI events trigger actions created by the [anchor]
   * composable functions.
   *
   * @param block The action to execute with the Anchor as receiver
   */
  public fun execute(block: suspend Anchor<@UnsafeVariance E, @UnsafeVariance S>.() -> Unit)
}

/**
 * Creates an AnchorScope from a ContainedScope.
 *
 * This factory function uses SAM conversion to create a clean implementation that
 * delegates to the provided ContainedScope. This is used internally by RememberAnchor
 * to provide safe action execution.
 *
 * @param E The Effect type
 * @param S The ViewState type
 * @param containedScope The contained scope to delegate to
 * @return An AnchorScope that delegates execution to the contained scope
 */
@PublishedApi
internal fun <E : Effect, S : ViewState> AnchorScope(
  containedScope: ContainedScope<out Anchor<E, S>, E, S>
): AnchorScope<E, S> = AnchorScope { block ->
  // Safe cast: block with receiver Anchor<E, S> can be called on any Anchor<*, *>
  // because Anchor<E, S> is a subtype of Anchor<*, *>
  @Suppress("UNCHECKED_CAST")
  containedScope.execute(block as suspend Anchor<*, *>.() -> Unit)
}
