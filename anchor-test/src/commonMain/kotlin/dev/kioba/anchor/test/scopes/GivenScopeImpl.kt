package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

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
  internal var onDomainError: (suspend Anchor<R, S, Err>.(Err) -> Unit)? = null

  @PublishedApi
  internal var defect: (suspend Anchor<R, S, Err>.(Throwable) -> Unit)? = null

  @AnchorTestDsl
  override fun initialState(
    f: () -> S,
  ) {
    initState = f()
  }

  @AnchorTestDsl
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

  @AnchorTestDsl
  override fun onDomainError(
    f: suspend Anchor<R, S, Err>.(Err) -> Unit,
  ) {
    onDomainError = f
  }

  @AnchorTestDsl
  override fun defect(
    f: suspend Anchor<R, S, Err>.(Throwable) -> Unit,
  ) {
    defect = f
  }
}
