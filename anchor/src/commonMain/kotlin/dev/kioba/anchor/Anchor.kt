package dev.kioba.anchor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.coroutines.CoroutineContext

public abstract class AnchorSink<E, S> : Anchor<E, S>()
  where
E : Effect,
S : ViewState {

  public abstract val viewState: StateFlow<S>

  public abstract val signals: SharedFlow<SignalProvider>

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

  @AnchorDsl
  public fun <R> withState(
    block: S.() -> R,
  ): R =
    state.run(block)
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
public object SubscriptionScope

@AnchorDsl
public interface SubscriptionAnchor {
  @AnchorDsl
  public suspend fun emit(
    block: SubscriptionScope.() -> Event,
  )
}

@AnchorDsl
public object SignalScope

@AnchorDsl
public interface SignalAnchor {
  @AnchorDsl
  public suspend fun post(
    block: SignalScope.() -> Signal,
  )
}
