package dev.kioba.anchor

import dev.kioba.anchor.dsl.Anchor
import dev.kioba.anchor.dsl.AnchorSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@DslMarker
internal annotation class AnchorDsl

public class AnchorScope<S, E>(
  initialState: () -> S,
  effects: () -> E,
  init: Anchor<*>,
) : AnchorStateScope<S>, AnchorInitScope, AnchorSignalScope, AnchorEffectScope<E>, AnchorDslScope {
  override val initManager: AnchorInitManager = AnchorInitManager(init)
  override val stateManager: AnchorStateManager<S> = AnchorStateManager(initialState)
  override val effectManager: AnchorEffectManager<E> = AnchorEffectManager(effects())
  override val signalManager: AnchorSignalManager = AnchorSignalManager()
  override val cancellationManager: AnchorCancellationManager = AnchorCancellationManager()
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