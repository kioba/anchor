package dev.kioba.anchor

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.onEach

public object SubscriptionScope

@AnchorDsl
public class SubscriptionsScope<E, S>(
  @PublishedApi
  internal val chain: Flow<Event>,
  @PublishedApi
  internal val anchor: Anchor<E, S>,
  @PublishedApi
  internal val flows: MutableList<Flow<*>> = mutableListOf(),
) where E : Effect, S : ViewState {
  @AnchorDsl
  public inline fun <I> Flow<I>.anchor(
    noinline block: Anchor<E, S>.(I) -> Unit,
  ): Flow<I> =
    onEach { value -> anchor.block(value) }

  @AnchorDsl
  public inline fun <I> Flow<I>.anchor(
    noinline block: (I) -> Action<Anchor<E, S>>,
  ): Flow<I> =
    onEach { value -> with(block(value)) { anchor.execute() } }

  @AnchorDsl
  public suspend inline fun <reified A> listen(
    crossinline block: E.(Flow<A>) -> Flow<*>,
  ) where A : Event {
    wrap {
      anchor.effects
        .block(filterIsInstance())
    }
  }

  @PublishedApi
  internal suspend fun wrap(
    func: suspend Flow<Event>.() -> Flow<*>,
  ) {
    flows.add(chain.func())
  }
}

@AnchorDsl
public suspend inline fun <R, E, S> R.emit(
  f: SubscriptionScope.() -> Event,
): Unit where R : Anchor<E, S>, E : Effect, S : ViewState =
  emitter
    .emit(SubscriptionScope.f())
