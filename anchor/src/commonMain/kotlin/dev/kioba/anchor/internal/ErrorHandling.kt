package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.coroutines.cancellation.CancellationException

public suspend inline fun <R, S, Err> catchDomainError(
  anchor: Anchor<R, S, Err>,
  noinline onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)?,
  block: () -> Unit,
) where R : Effect, S : ViewState, Err : Any {
  try {
    block()
  } catch (e: RaisedException) {
    @Suppress("UNCHECKED_CAST")
    val error = e.error as Err
    onDomainError?.invoke(anchor, error) ?: throw e
  }
}

public suspend inline fun <R, S, Err> catchDefects(
  anchor: Anchor<R, S, Err>,
  noinline defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
  block: () -> Unit,
) where R : Effect, S : ViewState, Err : Any {
  try {
    block()
  } catch (e: Throwable) {
    if (!e.isRoutableDefect()) throw e
    defect?.invoke(anchor, e) ?: throw e
  }
}

/**
 * Whether [catchDefects] may hand this to `defect`. A [CancellationException]
 * caught while the running coroutine is still active does not come from
 * cancelling it (a `withTimeout` expiry, awaiting a `Deferred` cancelled
 * elsewhere), so it is a failure like any other. The coroutine's own
 * cancellation and a [RaisedException] never are.
 */
@PublishedApi
internal suspend fun Throwable.isRoutableDefect(): Boolean =
  when (this) {
    is RaisedException -> false
    is CancellationException -> currentCoroutineContext().isActive
    else -> isNonFatal()
  }

public suspend inline fun <R, S, Err> safeExecute(
  anchor: Anchor<R, S, Err>,
  noinline onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)?,
  noinline defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
  block: () -> Unit,
) where R : Effect, S : ViewState, Err : Any {
  catchDefects(anchor, defect) {
    catchDomainError(anchor, onDomainError) {
      block()
    }
  }
}
