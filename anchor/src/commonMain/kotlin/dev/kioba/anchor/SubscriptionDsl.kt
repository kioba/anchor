package dev.kioba.anchor

import dev.kioba.anchor.internal.safeExecute
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
 * @param Err The domain error type.
 */
@AnchorDsl
public class SubscriptionsScope<R, S, Err>(
  @PublishedApi
  internal val chain: Flow<Event>,
  @PublishedApi
  internal val anchor: Anchor<R, S, Err>,
  /**
   * The [Effect] dependencies available in this scope.
   */
  public val effect: R,
  @PublishedApi
  internal val onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)? = null,
  @PublishedApi
  internal val defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)? = null,
  @PublishedApi
  internal val flows: MutableList<Flow<*>> = mutableListOf(),
) where R : Effect, S : ViewState, Err : Any {

  /**
   * Extension function to execute an action on the [Anchor] for each value emitted by a [Flow].
   *
   * If the action calls [Raise.raise], the error is routed to the `onDomainError` handler
   * without killing the subscription pipeline. If no handler is configured, the exception
   * propagates and cancels the subscription.
   *
   * @param I The type of values emitted by the [Flow].
   * @param action The action to execute on the [Anchor].
   * @return The original [Flow].
   */
  @AnchorDsl
  public fun <I> Flow<I>.anchor(
    action: Anchor<R, S, Err>.(I) -> Unit,
  ): Flow<I> =
    onEach { value ->
      safeExecute(anchor, onDomainError, defect) {
        anchor.action(value)
      }
    }

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
