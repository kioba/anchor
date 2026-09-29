package dev.kioba.anchor

import dev.kioba.anchor.internal.isHandledEscape
import dev.kioba.anchor.internal.recordHandledEscape
import dev.kioba.anchor.internal.safeExecute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
   * If the action calls [Raise.raise] or throws, the error is routed for that value, and the
   * chain keeps running:
   * - a domain error goes to `onDomainError`, or, if that is not configured, to `defect` as a
   *   [DomainDefectException], as `orDie` would;
   * - any other exception goes to `defect`. That includes a cancellation that does not come
   *   from cancelling the chain, such as a `withTimeout` expiry.
   *
   * If no matching handler is configured, or the handler itself throws, the error ends the
   * chain. It is not routed a second time on the way out.
   *
   * Errors thrown before this operator, such as in an inner flow of `flatMapLatest`, are not
   * covered: see [connect] and [anchorErrors].
   *
   * @param I The type of values emitted by the [Flow].
   * @param action The action to execute on the [Anchor].
   * @return The original [Flow].
   */
  public fun <I> Flow<I>.anchor(
    action: suspend Anchor<R, S, Err>.(I) -> Unit,
  ): Flow<I> =
    onEach { value ->
      try {
        safeExecute(anchor, onDomainError, defect) {
          anchor.action(value)
        }
      } catch (e: Throwable) {
        // It had its turn at the handlers; the chain must not route it again.
        recordHandledEscape(e)
        throw e
      }
    }

  /**
   * Routes an error from this [Flow] to the [Anchor]'s error handlers, then completes it.
   *
   * Like `catch`, it handles errors from upstream only. A domain error raised upstream goes
   * to `onDomainError`, and any other error goes to `defect`, as they do for an `.anchor {}`
   * action. The flow then completes instead of failing.
   *
   * Use it at the end of an inner flow, such as the one `flatMapLatest` starts for each event,
   * so that a failure ends only that inner flow. The [connect] handler keeps running, and the
   * next event starts a fresh inner flow. Each failing inner flow is routed once and never
   * retried.
   *
   * Cancelling the coroutine that collects the flow, including `flatMapLatest` switching to
   * the next event, is never routed. A cancellation that does not come from cancelling it,
   * such as a `withTimeout` expiry upstream, is an error like any other and goes to `defect`.
   * If the matching handler is not configured, or the handler itself throws, the error is
   * rethrown without being routed again, which ends the chain as it would without this
   * operator. So is an error that an `.anchor {}` upstream has already routed.
   *
   * Example:
   * ```kotlin
   * connect<MyEvent.Refresh> { events ->
   *   events
   *     .flatMapLatest { effect.fetchData().anchorErrors() }
   *     .anchor(MyAnchor::showData)
   * }
   * ```
   *
   * @param T The type of values emitted by the [Flow].
   * @return A [Flow] that emits the same values and completes after routing an error.
   */
  public fun <T> Flow<T>.anchorErrors(): Flow<T> =
    catch { error ->
      // An escape from an `.anchor {}` upstream was routed where it escaped.
      if (isHandledEscape(error)) throw error
      try {
        safeExecute(anchor, onDomainError, defect) {
          throw error
        }
      } catch (e: Throwable) {
        recordHandledEscape(e)
        throw e
      }
    }

  /**
   * Connects a handler to internal [Event]s of type [A].
   *
   * An error inside an `.anchor {}` action is routed for that value, and the handler keeps
   * running. An error that escapes the chain anywhere else, such as an operator before or
   * after `.anchor {}` or an inner flow of `flatMapLatest`, is routed to `onDomainError` or
   * `defect` once and ends this handler for the rest of the anchor's life. An exception
   * thrown by `onDomainError` or `defect` is never routed again: it ends this handler too. A
   * handler that has ended is not restarted. Other handlers keep running either way.
   *
   * To keep the handler alive when an inner flow fails, end the inner flow with
   * [anchorErrors].
   *
   * @param A The type of [Event] to connect to.
   * @param block A transformation block that takes a [Flow] of [A] and returns a [Flow] to be collected.
   *
   * Example:
   * ```kotlin
   * connect<MyEvent.Finished> { events ->
   *   events.onEach { /* do something */ }
   * }
   * ```
   */
  public suspend inline fun <reified A> connect(
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
