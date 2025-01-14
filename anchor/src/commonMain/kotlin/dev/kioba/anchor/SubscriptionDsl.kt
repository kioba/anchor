package dev.kioba.anchor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

@AnchorDsl
public class SubscriptionsScope<E, S>(
  @PublishedApi
  internal val chain: Flow<Event>,
  @PublishedApi
  internal val anchor: Anchor<E, S>,
  public val effect: E,
  @PublishedApi
  internal val flows: MutableList<Flow<*>> = mutableListOf(),
) where E : Effect, S : ViewState {

  @AnchorDsl
  public fun <I> Flow<I>.anchor(
    effect: Anchor<E, S>.(I) -> Unit,
  ): Flow<I> =
    onEach { value -> anchor.effect(value) }

  @AnchorDsl
  public fun <I> Flow<I>.anchor(
    block: (I) -> AnchorOf<Anchor<E, S>>,
  ): Flow<I> =
    onEach { value -> with(block(value)) { anchor.execute() } }

  @AnchorDsl
  public suspend inline fun <reified A> listen(
    crossinline block: (Flow<A>) -> Flow<*>,
  ) where A : Event {
    wrap {
      block(filterIsInstance())
    }
  }

  @PublishedApi
  internal suspend fun wrap(
    func: suspend Flow<Event>.() -> Flow<*>,
  ) {
    flows.add(chain.func())
  }
}
