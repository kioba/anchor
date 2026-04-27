package dev.kioba.anchor

/**
 * A restricted scope that provides state mutation, effect execution,
 * signal posting, and event emission — but NOT [Raise] or [DefectAnchor].
 *
 * This is the receiver type for error handlers (`onDomainError`, `defect`)
 * so that handlers can update state and trigger side effects without
 * accidentally re-raising errors.
 *
 * @param R The [Effect] type.
 * @param S The [ViewState] type.
 */
@AnchorDsl
public interface BaseAnchorScope<R, S> :
  MutableStateAnchor<S>,
  EffectAnchor<R>,
  SignalAnchor,
  SubscriptionAnchor
  where R : Effect,
        S : ViewState
