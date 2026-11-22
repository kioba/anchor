package dev.kioba.anchor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

/**
 * Marker for Anchor DSL.
 */
@DslMarker
internal annotation class AnchorDsl

/**
 * Marker interface for the UI state.
 *
 * All state classes used with Anchor must implement this interface.
 */
public interface ViewState

/**
 * Marker interface for side effects dependencies.
 *
 * This provides the dependencies required to execute side effects.
 */
public interface Effect

/**
 * Represents an empty [Effect] when no side effects dependencies are needed.
 */
public object EmptyEffect : Effect

/**
 * Marker interface for one-time signals emitted from an Anchor.
 *
 * Signals are typically used for UI events that don't change the state, like showing a Snackbar or navigating.
 */
public interface Signal

/**
 * Default [Signal] implementation.
 */
public object UnitSignal : Signal

/**
 * Marker interface for internal events within an Anchor.
 *
 * Events are used to trigger logic in the subscriptions block.
 */
public interface Event

/**
 * Event emitted when the Anchor is created.
 */
public object Created : Event

/**
 * Provides a [Signal] when collected.
 */
public fun interface SignalProvider {
  /**
   * Returns the [Signal].
   */
  public fun provide(): Signal
}

/**
 * A read-only view of an Anchor's state and signals.
 *
 * @param R The [Effect] type.
 * @param S The [ViewState] type.
 * @param Err The domain error type.
 */
public abstract class AnchorSink<R, S, Err> : Anchor<R, S, Err>()
  where
        R : Effect,
        S : ViewState,
        Err : Any {
  /**
   * The current state as a [StateFlow].
   */
  public abstract val viewState: StateFlow<S>

  /**
   * The stream of signals as a [SharedFlow].
   */
  public abstract val signals: SharedFlow<SignalProvider>
}

/**
 * Provides the ability to escalate a domain error to a defect.
 *
 * A defect reaches the `defect` handler configured via `create()`.
 * Use this when an error represents a programming mistake or
 * unrecoverable condition.
 *
 * @param Err The domain error type.
 */
@AnchorDsl
public interface DefectAnchor<Err> where Err : Any {
  /**
   * Escalates a domain error to a defect.
   *
   * @param error The domain error to escalate.
   */
  public fun orDie(error: Err): Nothing
}

/**
 * The core interface of the Anchor architecture.
 *
 * An Anchor manages the state of a component and handles side effects.
 * It combines state management, effect execution, cancellation, and event/signal emission.
 *
 * @param R The [Effect] type providing dependencies for side effects.
 * @param S The [ViewState] type representing the UI state.
 * @param Err The domain error type. Use [Nothing] when no domain errors are needed.
 */
@AnchorDsl
public abstract class Anchor<R, S, Err> :
  BaseAnchorScope<R, S>,
  CancellableAnchor<R, S, Err>,
  Raise<Err>,
  DefectAnchor<Err>
  where
        R : Effect,
        S : ViewState,
        Err : Any

/**
 * Provides access to the current state.
 *
 * @param S The [ViewState] type.
 */
@AnchorDsl
public interface StateAnchor<S> where S : ViewState {
  /**
   * The current state.
   */
  public val state: S

  /**
   * Executes a block with the current state as a receiver.
   *
   * @param block The block to execute.
   * @return The result of the block.
   */
  public fun <T> withState(
    block: S.() -> T,
  ): T =
    state.run(block)
}

/**
 * Provides the ability to modify the state.
 *
 * @param S The [ViewState] type.
 */
@AnchorDsl
public interface MutableStateAnchor<S> : StateAnchor<S> where S : ViewState {
  /**
   * Updates the state using the provided reducer.
   *
   * @param reducer A function that takes the current state and returns a new state.
   *
   * Example:
   * ```kotlin
   * reduce { copy(count = count + 1) }
   * ```
   */
  public fun reduce(
    reducer: S.() -> S,
  )
}

/**
 * Provides the ability to execute side effects.
 *
 * @param R The [Effect] type.
 */
@AnchorDsl
public interface EffectAnchor<R> where R : Effect {
  /**
   * Executes a side effect block using the provided effect dependencies.
   *
   * @param coroutineContext The [CoroutineContext] to run the block in. Defaults to [Dispatchers.IO].
   * @param block The block to execute with [R] as a receiver.
   * @return The result of the side effect.
   *
   * Example:
   * ```kotlin
   * val result = effect { repository.getData() }
   * ```
   */
  public suspend fun <T> effect(
    coroutineContext: CoroutineContext = Dispatchers.IO,
    block: suspend R.() -> T,
  ): T
}

/**
 * Provides the ability to manage cancellable jobs.
 *
 * @param R The [Effect] type.
 * @param S The [ViewState] type.
 * @param Err The domain error type.
 */
@AnchorDsl
public interface CancellableAnchor<R, S, Err> where R : Effect, S : ViewState, Err : Any {
  /**
   * Executes a block that can be cancelled by its [key].
   *
   * If a job with the same key is already running, it will be cancelled before the new block is executed.
   *
   * @param key The identifier for the cancellable job.
   * @param block The block to execute.
   *
   * Example:
   * ```kotlin
   * cancellable("search") {
   *   val results = effect { search(query) }
   *   reduce { copy(results = results) }
   * }
   * ```
   */
  public suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  )
}

/**
 * Scope for internal events.
 */
@AnchorDsl
public object SubscriptionScope

/**
 * Provides the ability to emit internal events.
 */
@AnchorDsl
public interface SubscriptionAnchor {
  /**
   * Emits an internal event.
   *
   * Every event goes into one queue, in call order. Until the anchor's subscriptions start, events
   * stay there. `init` runs first, so this covers the events `init` emits and any emitted by
   * actions while it runs. When subscriptions start, every `connect` handler gets an inbox of its
   * own that begins with [Created], and from then on each event leaves the queue in order and is
   * copied into every inbox. So each handler receives [Created], with the state `init` set already
   * in place, then every event in emission order, however late it starts collecting: `flowOn`,
   * `shareIn` or a `yield()` before it collects lose nothing. An event a handler emits while it
   * handles [Created] reaches every handler, whether declared before or after it. A handler that
   * collects its events again, for example through `retry`, carries on from where it stopped.
   *
   * An inbox holds 128 events. While its handler is collecting, a full inbox makes delivery wait,
   * so a slow handler holds up the others instead of losing events. While it is not collecting,
   * because it has not started yet, has finished, or never reads its events, an event that finds
   * its inbox full is dropped for that handler alone.
   *
   * `init` must return: until it does, the handlers do not start and emitted events stay queued.
   * Observe long-lived sources from `subscriptions`, not from `init`. The handlers start however
   * `init` ends, including when it fails or is cancelled, unless the anchor itself is being torn
   * down. A failure in `init` that no handler catches is still reported like a failing action,
   * which crashes the app on Android and iOS. If the `subscriptions` block itself fails, no
   * handler exists and later events are dropped.
   *
   * The queue holds 64 events. Before subscriptions start nothing leaves it, so `init` can emit at
   * most 64 events, fewer if actions emitted some while it ran. A further `emit` from `init` waits
   * for handlers that start only once `init` returns, and startup hangs.
   *
   * `emit` returns once the event is in the queue; it does not wait for any handler to process it.
   * Once subscriptions have started, it suspends only when the queue is full because delivery is
   * waiting on a full inbox, and resumes once that handler catches up.
   *
   * An action run by a `connect` handler, or an error handler invoked from it, can call `emit`.
   * That handler takes no new events until the invocation returns, so an invocation that emits
   * more than 192 events (fewer if others are already pending) fills its inbox and the queue and
   * suspends forever.
   *
   * @param block A block that returns the [Event] to emit.
   *
   * Example:
   * ```kotlin
   * emit { MyEvent.Finished }
   * ```
   */
  public suspend fun emit(
    block: SubscriptionScope.() -> Event,
  )
}

/**
 * Scope for external signals.
 */
@AnchorDsl
public object SignalScope

/**
 * Provides the ability to post external signals.
 */
@AnchorDsl
public interface SignalAnchor {
  /**
   * Posts a signal that can be handled by the UI.
   *
   * @param block A block that returns the [Signal] to post.
   *
   * Example:
   * ```kotlin
   * post { CounterSignal.Increment }
   * ```
   */
  public suspend fun post(
    block: SignalScope.() -> Signal,
  )
}
