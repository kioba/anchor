package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@PublishedApi
internal class GivenScopeImpl<R, S> : GivenScope<R, S> where R : Effect, S : ViewState {
  @PublishedApi
  internal var initState: S? = null

  @PublishedApi
  internal var effectScope: R? = null

  @PublishedApi
  internal val effects: MutableList<(R.() -> Unit)> = mutableListOf()

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
}
