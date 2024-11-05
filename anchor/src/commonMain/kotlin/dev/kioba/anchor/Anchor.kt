package dev.kioba.anchor

import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesRefinedState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onSubscription

public data class Anchor<E, S>(
  internal val initialState: () -> S,
  internal val effectScope: () -> E,
  internal val init: (() -> AnchorOf<Anchor<E, S>>)? = null,
  internal val subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)? = null,
) where E : Effect, S : ViewState {
  @PublishedApi
  internal val effects: E = effectScope()

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _viewState: MutableStateFlow<S> = MutableStateFlow(initialState())

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _signals: MutableSharedFlow<Signal> = MutableSharedFlow()

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _emitter: MutableSharedFlow<Event> = MutableSharedFlow()

  @PublishedApi
  internal val jobs: MutableMap<Any, Job> = mutableMapOf()

  @NativeCoroutinesRefinedState
  public val viewState: StateFlow<S> = _viewState.asStateFlow()

  public val signals: SharedFlow<Signal> = _signals.asSharedFlow()

  public val emitter: SharedFlow<Event> = _emitter.asSharedFlow()
}

internal class AnchorRuntime<E, S>(
  val anchor: Anchor<E, S>,
) where E : Effect, S : ViewState {
  @PublishedApi
  internal val chain: SharedFlow<Event> =
    anchor._emitter
      .asSharedFlow()
      .onSubscription { emit(Created) }

  internal suspend fun consumeInitial() {
    when {
      anchor.init != null ->
        with(anchor.init.invoke()) {
          anchor.execute()
        }
    }
  }

  private suspend fun <T : Event> SharedFlow<T>.handlers(): List<Flow<*>> =
    anchor.subscriptions
      ?.let { function ->
        SubscriptionsScope(this, anchor = anchor)
          .apply { function() }
      }?.flows
      .orEmpty()

  public suspend fun CoroutineScope.subscribe(): Job =
    chain
      .handlers()
      .merge()
      .launchIn(this)
}
