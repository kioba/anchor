package dev.kioba.anchor

import dev.kioba.anchor.dsl.Action
import dev.kioba.anchor.dsl.Anchor
import dev.kioba.anchor.dsl.AnchorSignal
import dev.kioba.anchor.dsl.Created
import dev.kioba.anchor.dsl.SubscriptionsScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.onSubscription

@DslMarker
internal annotation class AnchorDsl

public class AnchorScope<S, E>(
  initialState: () -> S,
  effectScope: () -> E,
  subscriptions: SubscriptionsScope<S, E>.() -> Unit,
  init: Anchor<*>,
) : AnchorStateScope<S>,
  AnchorInitScope,
  AnchorSubscriptionScope<S, E>,
  AnchorSignalScope,
  AnchorEffectScope<E>,
  AnchorDslScope {
  override val cancellationManager: AnchorCancellationManager = AnchorCancellationManager()
  override val effectManager: AnchorEffectManager<E> = AnchorEffectManager(effectScope())
  override val initManager: AnchorInitManager = AnchorInitManager(init)
  override val signalManager: AnchorSignalManager = AnchorSignalManager()
  override val stateManager: AnchorStateManager<S> = AnchorStateManager(initialState)
  override val subscriptionManager: AnchorSubscriptionManager<S, E> =
    AnchorSubscriptionManager(this, subscriptions)
}

public interface AnchorDslScope {
  public val cancellationManager: AnchorCancellationManager
}

public interface AnchorSignalScope {
  public val signalManager: AnchorSignalManager
}

public interface AnchorStateScope<S> {
  public val stateManager: AnchorStateManager<S>
}

public interface AnchorEffectScope<E> {
  public val effectManager: AnchorEffectManager<E>
}

public interface AnchorInitScope {
  public val initManager: AnchorInitManager
}

public interface AnchorSubscriptionScope<S, E> {
  public val subscriptionManager: AnchorSubscriptionManager<S, E>
}

public class AnchorCancellationManager {
  @PublishedApi
  internal val jobs: MutableMap<Any, Job> = mutableMapOf()
}

public class AnchorInitManager(
  @PublishedApi
  internal val init: Anchor<*>,
)

public class AnchorStateManager<S>(
  @PublishedApi
  internal val initialState: () -> S,
  @PublishedApi
  internal val states: MutableStateFlow<S> = MutableStateFlow(initialState()),
)

public class AnchorEffectManager<E>(
  @PublishedApi
  internal val effectScope: E,
)

public class AnchorSignalManager {
  @PublishedApi
  internal val signals: MutableSharedFlow<AnchorSignal> = MutableSharedFlow()
}

public class AnchorSubscriptionManager<S, E>(
  @PublishedApi
  internal val anchor: AnchorScope<S, E>,
  @PublishedApi
  internal val subscriptions: SubscriptionsScope<S, E>.() -> Unit,
) {
  @PublishedApi
  internal val emitter: MutableSharedFlow<Action> = MutableSharedFlow()

  @PublishedApi
  internal val chain: SharedFlow<Action> = emitter.onSubscription { emit(Created) }
  public fun subscribe(): List<Flow<Anchor<*>>> {
    val collection = mutableListOf<Flow<Anchor<*>>>()
    SubscriptionsScope<S, E>(anchor.effectManager.effectScope, chain, collection).subscriptions()
    return collection
  }
}