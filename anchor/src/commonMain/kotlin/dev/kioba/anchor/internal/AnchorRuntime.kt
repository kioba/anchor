package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorSink
import dev.kioba.anchor.Created
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.Event
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import dev.kioba.anchor.SignalScope
import dev.kioba.anchor.SubscriptionScope
import dev.kioba.anchor.SubscriptionsScope
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@PublishedApi
internal class AnchorRuntime<R, S, Err>(
  val initialState: () -> S,
  val effectScope: () -> R,
  internal val init: (suspend Anchor<R, S, Err>.() -> Unit)? = null,
  internal val subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)? = null,
  internal val onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)? = null,
  internal val defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)? = null,
) : AnchorSink<R, S, Err>()
  where
        R : Effect,
        S : ViewState,
        Err : Any {
  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _viewState: MutableStateFlow<S> = MutableStateFlow(initialState())

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _signals: MutableSharedFlow<SignalProvider> =
    MutableSharedFlow(extraBufferCapacity = 64)

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _emitter: MutableSharedFlow<Event> = MutableSharedFlow()

  /**
   * Map storing the latest cancellable job and its completion chain, keyed by identifier.
   * Access is guarded by [jobsMutex] to prevent race conditions.
   */
  @PublishedApi
  internal val jobs: MutableMap<Any, KeyedJob> = mutableMapOf()

  /**
   * Mutex protecting access to the [jobs] map to ensure thread-safe
   * job cancellation and prevent race conditions.
   */
  private val jobsMutex = Mutex()

  internal val effect = effectScope()

  override val viewState: StateFlow<S> = _viewState.asStateFlow()

  override val signals: SharedFlow<SignalProvider> = _signals.asSharedFlow()

  private val emitter: SharedFlow<Event> =
    _emitter
      .asSharedFlow()
      .onSubscription { emit(Created) }

  internal suspend fun consumeInitial() {
    init?.invoke(this@AnchorRuntime)
  }

  private suspend fun <T : Event> SharedFlow<T>.handlers(): List<Flow<*>> =
    SubscriptionsScope<R, S, Err>(
      chain = this,
      anchor = this@AnchorRuntime,
      effect = effect,
      onDomainError = onDomainError,
      defect = defect,
    ).also { scope -> subscriptions?.invoke(scope) }
      .flows
      .map { flow ->
        flow.catch { e ->
          safeExecute(this@AnchorRuntime, onDomainError, defect) {
            throw e
          }
        }
      }

  suspend fun CoroutineScope.subscribe(): Job {
    val handlers = emitter.handlers()
    // SupervisorJob: a thrown subscription must not cancel siblings.
    val supervisor = SupervisorJob(parent = this.coroutineContext[Job])
    // Without a handler, an exception a subscription rethrows past
    // safeExecute (no defect handler configured) reaches each platform's
    // default uncaught-exception path. The JVM/Android default just logs it;
    // Kotlin/Native's default aborts the whole process. This handler makes
    // "contained, not fatal" consistent across platforms — the isolation
    // SupervisorJob provides already keeps it from touching sibling flows.
    val containment = CoroutineExceptionHandler { _, _ -> }
    val supervised = CoroutineScope(this.coroutineContext + supervisor + containment)
    for (flow in handlers) {
      flow.launchIn(supervised)
    }
    return supervisor
  }

  // DSL

  override val state: S
    get() = _viewState.value

  override fun reduce(
    reducer: S.() -> S,
  ): Unit =
    _viewState.update(reducer)

  override fun raise(
    error: Err,
  ): Nothing =
    throw RaisedException(error)

  override fun orDie(
    error: Err,
  ): Nothing =
    throw DomainDefectException(error)

  override suspend fun <T> effect(
    coroutineContext: CoroutineContext,
    block: suspend R.() -> T,
  ): T =
    withContext(coroutineContext) {
      effect.block()
    }

  /**
   * Executes a cancellable operation identified by [key].
   *
   * If a previous operation with the same key is still running, it will be cancelled,
   * and the new operation starts only after the previous one has completed. This is
   * useful for debouncing operations like search queries where only the latest request
   * should run.
   *
   * Thread-safe: A mutex guards the jobs map, but it is never held across a join. Only
   * non-suspending bookkeeping (map updates, `cancel()`, `launch`) runs under it, so a
   * slow or non-cooperative operation on one key never delays an unrelated key. Each
   * entry carries a `done` job that completes only after its own job and every
   * predecessor for the same key have completed, so at most one block per key runs at
   * a time, even when a waiting caller is cancelled.
   *
   * Memory-safe: A job removes its own entry when it finishes, using identity comparison
   * so it never removes a newer job that may have replaced it. An entry that cannot be
   * removed yet (its predecessor chain is still running, or it was cancelled before it
   * started) is purged on the next `cancellable` call.
   *
   * @param key Identifier for this cancellable operation. Operations with the same
   *        key will cancel each other.
   * @param block The operation to execute. If a previous operation with the same key
   *        is running, it will be cancelled, and this block starts after it has completed.
   */
  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    coroutineScope {
      var raised: RaisedException? = null

      val jobToWait =
        jobsMutex.withLock {
          // Only non-suspending work happens under the lock: never join here, or a
          // slow job on one key would stall every other key.
          jobs.entries.removeAll { it.value.done.isCompleted }
          val previous = jobs[key]
          previous?.job?.cancel()

          val newJob =
            launch {
              val self = coroutineContext[Job]
              try {
                // Wait until the previous holder of this key and all of its
                // predecessors have completed. Cancellable: a successor waits on
                // this job's `done`, which also waits for `previous.done`.
                previous?.done?.join()
                block()
              } catch (e: RaisedException) {
                raised = e
                throw e
              } finally {
                withContext(NonCancellable) {
                  jobsMutex.withLock {
                    // Remove only when still current AND the predecessor chain is
                    // done; otherwise the next caller must still wait on `done`.
                    // Leftovers are purged on the next acquisition.
                    val predecessorDone = previous?.done?.isCompleted ?: true
                    if (jobs[key]?.job === self && predecessorDone) jobs.remove(key)
                  }
                }
              }
            }
          val done = Job()
          newJob.invokeOnCompletion {
            val prevDone = previous?.done
            if (prevDone == null) done.complete() else prevDone.invokeOnCompletion { done.complete() }
          }
          jobs[key] = KeyedJob(newJob, done)
          newJob
        }

      // Wait for the job to complete (outside the lock)
      jobToWait.join()

      // Propagate RaisedException after join — CancellationException semantics
      // cause join() to complete normally, but the domain error must propagate.
      raised?.let { throw it }
    }
  }

  override suspend fun post(
    block: SignalScope.() -> Signal,
  ) {
    val signal = SignalScope.block()
    _signals.emit(SignalProvider { signal })
  }

  override suspend fun emit(
    block: SubscriptionScope.() -> Event,
  ): Unit =
    _emitter
      .emit(SubscriptionScope.block())
}

/**
 * A `cancellable` [job] paired with its [done] marker, which completes only after [job]
 * and every earlier job for the same key have completed.
 */
@PublishedApi
internal class KeyedJob(
  val job: Job,
  val done: Job,
)
