package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ErrorScope
import dev.kioba.anchor.ViewState

@PublishedApi
internal class GivenScopeImpl<R, S, Err> :
  GivenScope<R, S, Err>
  where R : Effect, S : ViewState, Err : Any {
  @PublishedApi
  internal var initState: S? = null

  @PublishedApi
  internal var effectScope: R? = null

  @PublishedApi
  internal val effects: MutableList<(R.() -> Unit)> = mutableListOf()

  @PublishedApi
  internal var onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)? = null

  @PublishedApi
  internal var defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)? = null

  @PublishedApi
  internal var suppressInitialState: Boolean = false

  override fun initialState(
    f: () -> S,
  ) {
    if (!suppressInitialState) {
      initState = f()
    }
  }

  override suspend fun effect(
    f: R.() -> Unit,
  ) {
    effects.add(f)
  }

  override suspend fun effectScope(
    f: () -> R,
  ) {
    effectScope = f()
  }

  override fun onDomainError(
    f: suspend ErrorScope<R, S>.(Err) -> Unit,
  ) {
    onDomainError = f
  }

  override fun defect(
    f: suspend ErrorScope<R, S>.(Throwable) -> Unit,
  ) {
    defect = f
  }
}
