package dev.kioba.anchor

import dev.kioba.anchor.dsl.AnchorEffect
import dev.kioba.anchor.dsl.AnchorSignal
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

@DslMarker
internal annotation class AnchorDsl

public class AnchorScope<S>(
  initialState: () -> S,
  init: AnchorEffect<*>,
) : AnchorStateScope<S>, AnchorInitScope, AnchorSignalScope, AnchorDslScope {
  override val initManager: AnchorInitManager = AnchorInitManager(init)
  override val stateManager: AnchorStateManager<S> = AnchorStateManager(initialState)
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

public interface AnchorInitScope {
  public val initManager: AnchorInitManager
}

public class AnchorCancellationManager {
  @PublishedApi
  internal val jobs: MutableMap<Any, Job> = mutableMapOf()
}

public class AnchorInitManager(
  @PublishedApi
  internal val init: AnchorEffect<*>,
)

public class AnchorStateManager<S>(
  @PublishedApi
  internal val initialState: () -> S,
  @PublishedApi
  internal val states: MutableStateFlow<S> = MutableStateFlow(initialState()),
)

public class AnchorSignalManager {
  @PublishedApi
  internal val signals: MutableSharedFlow<AnchorSignal> = MutableSharedFlow()
}