package dev.kioba.anchor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

@DslMarker
internal annotation class AnchorDsl

public interface ViewState

public interface Effect

public object EmptyEffect : Effect

public interface Signal

public object UnitSignal : Signal

public interface Event

public object Created : Event

public abstract class AnchorSink<E, S> : Anchor<E, S>()
  where
E : Effect,
S : ViewState {

  public abstract val viewState: StateFlow<S>

  public abstract val signals: SharedFlow<Signal>

}

@AnchorDsl
public abstract class Anchor<E, S> : MutableStateAnchor<S>,
  EffectAnchor<E>,
  CancellableAnchor<E, S>,
  SubscriptionAnchor,
  SignalAnchor
  where
E : Effect,
S : ViewState

@AnchorDsl
public interface StateAnchor<S> where S : ViewState {
  @AnchorDsl
  public val state: S
}

@AnchorDsl
public interface MutableStateAnchor<S> : StateAnchor<S> where S : ViewState {
  @AnchorDsl
  public fun reduce(
    reducer: S.() -> S,
  )
}

@AnchorDsl
public interface EffectAnchor<E> where E : Effect {
  @AnchorDsl
  public suspend fun <R> effect(
    coroutineContext: CoroutineContext = Dispatchers.IO,
    block: suspend E.() -> R,
  ): R
}

@AnchorDsl
public interface CancellableAnchor<E, S> where E : Effect, S : ViewState {
  @AnchorDsl
  public suspend fun cancellable(
    key: Any,
    block: suspend Anchor<E, S>.() -> Unit,
  )
}

@AnchorDsl
public interface SubscriptionAnchor {
  @AnchorDsl
  public fun emit(event: Event)
}

@AnchorDsl
public interface SignalAnchor {
  @AnchorDsl
  public fun post(signal: Signal)
}
