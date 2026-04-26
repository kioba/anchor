package dev.kioba.anchor.internal

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.RaisedException
import dev.kioba.anchor.ViewState

@PublishedApi
internal suspend inline fun <R, S, Err> catchDomainError(
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

@PublishedApi
internal suspend inline fun <R, S, Err> catchDefects(
  anchor: Anchor<R, S, Err>,
  noinline defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
  block: () -> Unit,
) where R : Effect, S : ViewState, Err : Any {
  try {
    block()
  } catch (e: Throwable) {
    if (!e.isNonFatal()) throw e
    defect?.invoke(anchor, e) ?: throw e
  }
}

@PublishedApi
internal suspend inline fun <R, S, Err> safeExecute(
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
