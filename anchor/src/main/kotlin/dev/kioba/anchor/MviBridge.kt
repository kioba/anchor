package dev.kioba.anchor

import dev.kioba.anchor.dsl.Action
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

public class MviBridge<S>(
  initialState: () -> S,
  @PublishedApi
  internal val initialAction: Action<*>? = null,
) {
  @PublishedApi
  internal val stateChannel: MutableStateFlow<S> = MutableStateFlow(initialState())

  @PublishedApi
  internal val effectChannel: MutableSharedFlow<MviEffect> = MutableSharedFlow()
}
