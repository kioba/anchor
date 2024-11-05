package dev.kioba.anchor.test.scopes

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.AnchorTestDsl

@PublishedApi
internal class GivenScopeImpl<E, S> : GivenScope<E, S> where E : Effect, S : ViewState {
  @PublishedApi
  internal var initState: S? = null

  @PublishedApi
  internal var effectScope: E? = null

  @PublishedApi
  internal val effects: MutableList<(E.() -> Unit)> = mutableListOf()

  @AnchorTestDsl
  override fun initialState(
    f: () -> S,
  ) {
    initState = f()
  }

  @AnchorTestDsl
  override suspend fun effect(
    f: E.() -> Unit,
  ) {
    effects.add(f)
  }

  override suspend fun effectScope(
    f: () -> E,
  ) {
    effectScope = f()
  }
}
