package dev.kioba.anchor

import kotlin.coroutines.cancellation.CancellationException

/**
 * Short-circuits execution within an action.
 *
 * Extends [CancellationException] so it propagates correctly through coroutine
 * boundaries (`launch`/`join`/`cancellable`). The `onDomainError` handler catches
 * this before the standard [CancellationException] re-throw.
 *
 * @property error The domain error value that was raised.
 */
public class RaisedException(
  public val error: Any,
) : CancellationException("Domain error raised: $error")

/**
 * Escalation that reaches the `defect` handler, bypassing domain error handling.
 *
 * Extends [RuntimeException] (not [CancellationException]) so it is NOT confused
 * with scope cancellation and reaches the outer `catch (e: Throwable)` in `execute`.
 *
 * @property error The domain error value that was escalated.
 */
public class DomainDefectException(
  public val error: Any,
) : RuntimeException("Domain defect: $error")
