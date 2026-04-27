package dev.kioba.anchor.internal

import dev.kioba.anchor.Raise
import dev.kioba.anchor.RaisedException

/**
 * Identity token used by standalone [recover] to match only errors raised
 * within its own scope. Anchor-level [recover] does not use a token.
 */
@PublishedApi
internal class RaiseToken

/**
 * Minimal [Raise] implementation for standalone [recover] blocks.
 *
 * Each instance is bound to a [RaiseToken] so that only errors raised
 * inside this scope are caught by the enclosing `recover`.
 */
@PublishedApi
internal class RaiseScope<Err : Any>(
  @PublishedApi internal val token: RaiseToken,
) : Raise<Err> {
  override fun raise(error: Err): Nothing =
    throw RaisedException(error, token)
}
