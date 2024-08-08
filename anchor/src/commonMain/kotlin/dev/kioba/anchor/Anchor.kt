package dev.kioba.anchor

import com.rickclephas.kmp.nativecoroutines.NativeCoroutinesState
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

public class Anchor<E, S>(
  public val initialState: () -> S,
  effectScope: () -> E,
  private val init: (() -> Action<Anchor<E, S>>)? = null,
  private val subscriptions: (suspend SubscriptionsScope<E, S>.() -> Unit)? = null,
) where E : Effect, S : ViewState {
  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _viewState: MutableStateFlow<S> = MutableStateFlow(initialState())

  @NativeCoroutinesState
  public val viewState: StateFlow<S> = _viewState.asStateFlow()

  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _signals: MutableSharedFlow<Signal> = MutableSharedFlow()

  public val signals: SharedFlow<Signal> = _signals.asSharedFlow()

  @PublishedApi
  internal val jobs: MutableMap<Any, Job> = mutableMapOf()

  @PublishedApi
  internal val emitter: MutableSharedFlow<Event> = MutableSharedFlow()

  @PublishedApi
  internal val chain: SharedFlow<Event> =
    emitter
      .asSharedFlow()
      .onSubscription { emit(Created) }

  @PublishedApi
  internal val effects: E = effectScope()

  internal suspend fun consumeInitial() {
    when {
      init != null ->
        with(init.invoke()) {
          execute()
        }
    }
  }

  private suspend fun <T : Event> SharedFlow<T>.handlers(): List<Flow<*>> =
    subscriptions
      ?.let { function ->
        SubscriptionsScope(this, anchor = this@Anchor)
          .apply { function() }
      }?.flows
      .orEmpty()

  public suspend fun CoroutineScope.subscribe(): Job =
    chain
      .handlers()
      .merge()
      .launchIn(this)
}
