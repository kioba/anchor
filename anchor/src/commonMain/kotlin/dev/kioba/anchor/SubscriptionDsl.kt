package dev.kioba.anchor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

/**
 * Scope for defining event subscriptions within an [Anchor].
 *
 * This scope provides a DSL for listening to [Event]s and reacting to them by executing actions on the [Anchor].
 *
 * @param R The [Effect] type.
 * @param S The [ViewState] type.
 */
@AnchorDsl
public class SubscriptionsScope<R, S>(
  @PublishedApi
  internal val chain: Flow<Event>,
  @PublishedApi
  internal val anchor: Anchor<R, S>,
  /**
   * The [Effect] dependencies available in this scope.
   */
  public val effect: R,
  @PublishedApi
  internal val flows: MutableList<Flow<*>> = mutableListOf(),
) where R : Effect, S : ViewState {

  /**
   * Extension function to execute an action on the [Anchor] for each value emitted by a [Flow].
   *
   * @param I The type of values emitted by the [Flow].
   * @param effect The action to execute on the [Anchor].
   * @return The original [Flow].
   */
  @AnchorDsl
  public fun <I> Flow<I>.anchor(
    effect: Anchor<R, S>.(I) -> Unit,
  ): Flow<I> =
    onEach { value -> anchor.effect(value) }

  /**
   * Listens for internal [Event]s of type [A].
   *
   * @param A The type of [Event] to listen for.
   * @param block A transformation block that takes a [Flow] of [A] and returns a [Flow] to be collected.
   *
   * Example:
   * ```kotlin
   * listen<MyEvent.Finished> { events ->
   *   events.onEach { /* do something */ }
   * }
   * ```
   */
  @AnchorDsl
  public suspend inline fun <reified A> listen(
    crossinline block: (Flow<A>) -> Flow<*>,
  ) where A : Event {
    wrap {
      block(filterIsInstance())
    }
  }

  @PublishedApi
  internal suspend fun wrap(
    func: suspend Flow<Event>.() -> Flow<*>,
  ) {
    flows.add(chain.func())
  }
}
