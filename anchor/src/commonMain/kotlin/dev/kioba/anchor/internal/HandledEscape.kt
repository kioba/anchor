package dev.kioba.anchor.internal

import kotlinx.coroutines.currentCoroutineContext
import kotlin.concurrent.Volatile
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Per-listener record of the exception that last escaped an `.anchor {}` step
 * or `anchorErrors()`. That exception already had its turn at `onDomainError`
 * and `defect` (or had no matching handler), so the chain-level `catch` in
 * `AnchorRuntime.handlers()` must not route it again. Without the record, a
 * `defect` handler that throws is invoked a second time with its own
 * exception. Only the last escape is kept: two steps of one listener failing
 * concurrently (e.g. under `merge`) can still route the earlier one twice.
 */
internal class HandledEscape : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<HandledEscape>

  @Volatile
  private var last: Throwable? = null

  fun record(e: Throwable) {
    last = e
  }

  /**
   * Also matches a copy of the recorded exception: with stack-trace recovery
   * on (kotlinx debug mode on the JVM), an exception that crosses a coroutine
   * boundary, such as out of a `flatMapLatest` inner flow, arrives as a new
   * instance of the same class whose [Throwable.cause] is the original. A
   * different exception that merely wraps it is not a copy.
   */
  fun isRecorded(e: Throwable): Boolean {
    val recorded = last ?: return false
    return e === recorded || (e.cause === recorded && e::class == recorded::class)
  }
}

internal suspend fun recordHandledEscape(e: Throwable) {
  currentCoroutineContext()[HandledEscape]?.record(e)
}

internal suspend fun isHandledEscape(e: Throwable): Boolean =
  currentCoroutineContext()[HandledEscape]?.isRecorded(e) == true
