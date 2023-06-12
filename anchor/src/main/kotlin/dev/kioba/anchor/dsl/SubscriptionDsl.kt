package dev.kioba.anchor.dsl

import dev.kioba.anchor.AnchorDsl
import dev.kioba.anchor.AnchorDslScope
import dev.kioba.anchor.AnchorSubscriptionScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlin.reflect.KClass

public object SubscriptionScope


@AnchorDsl
public class SubscriptionsScope<S, E>(
  @PublishedApi
  internal val effectScope: E,
  @PublishedApi
  internal val chain: SharedFlow<Action>,
  internal val list: MutableList<Flow<Anchor<*>>> = mutableListOf(),
) {
  public fun <D, R> Flow<R>.anchorWith(
    block: (R) -> Anchor<D>,
  ) where D : AnchorDslScope {
    map { value -> block(value) }
      .let { list.add(it) }
  }

  public fun <D, R> Flow<R>.anchor(
    block: context(D) (R) -> Unit,
  ) where D : AnchorDslScope {
    anchorWith { Anchor<D> { block(this, it) } }
  }
}

public inline fun <S, E, reified R> SubscriptionsScope<S, E>.listen(
  noinline block: E.() -> Flow<R>,
): Flow<R> =
  ChainCollector(mutableListOf(ActionType(Created::class)))
    .chain(block)


public interface Action
public object Created : Action

public class ChainCollector(
  @PublishedApi
  internal val types: MutableList<ActionType> = mutableListOf(),
)

public inline fun <reified A> ChainCollector.and(): ChainCollector
  where A : Action =
  apply { types.add(ActionType(A::class)) }

public inline fun <reified A> ChainCollector.until(): ChainCollector
  where A : Action =
  apply { types.add(ActionType(A::class, isCancel = true)) }

@Suppress("UnusedReceiverParameter")
public inline fun <reified A> SubscriptionsScope<*, *>.listenFor(): ChainCollector
  where A : Action =
  ChainCollector(mutableListOf(ActionType(A::class)))

@Suppress("UnusedReceiverParameter")
public fun SubscriptionsScope<*, *>.listenCreated(): ChainCollector =
  ChainCollector(mutableListOf(ActionType(Created::class)))

public class ActionType(
  @PublishedApi
  internal val value: KClass<out Action>,
  @PublishedApi
  internal val isCancel: Boolean = false,
)

context(SubscriptionsScope<*, E>)
  @OptIn(ExperimentalCoroutinesApi::class)
  public inline fun <E, reified R> ChainCollector.chain(
  noinline block: E.() -> Flow<R>,
): Flow<R> =
  chain.mapNotNull { action ->
    types.find { it.value.isInstance(action) }
      ?.let { type -> type to action }
  }
    .flatMapLatest { (type, _) ->
      when {
        type.isCancel ->
          currentCoroutineContext().cancel()
      }
      block(effectScope)
    }

@AnchorDsl
public suspend inline fun <E> E.subscribe(
  block: SubscriptionScope.() -> Action,
): Unit where
  E : AnchorSubscriptionScope<*, *> =
  subscriptionManager
    .emitter
    .emit(SubscriptionScope.block())
