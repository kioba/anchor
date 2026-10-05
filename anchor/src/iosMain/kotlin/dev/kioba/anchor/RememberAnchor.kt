package dev.kioba.anchor

/**
 * Creates an [Anchor] for iOS without anything that owns it.
 *
 * Nothing retains or releases the anchor: every call creates a new one whose `init` and subscriptions run until the
 * process ends. Use [createAnchor] instead, hold the [AnchorContainer] it returns, and call
 * [AnchorContainer.clear] from your view's teardown.
 *
 * @param S The [ViewState] type.
 * @param E The [Effect] type.
 * @param scope Factory function that creates the [Anchor] instance.
 * @param customKey Optional key for Anchor storage.
 * @return The [Anchor] instance.
 */
@Deprecated(
  message =
    "rememberAnchor neither retains nor disposes the anchor; every call " +
      "creates a runtime whose coroutines are never cancelled. Use createAnchor() " +
      "and call clear() from your view's teardown.",
  replaceWith = ReplaceWith("createAnchor(scope, customKey)"),
)
public fun <S, E> rememberAnchor(
  scope: (RememberAnchorScope) -> Anchor<E, S, *>,
  customKey: String? = null,
): Anchor<E, S, *>
  where
        E : Effect,
        S : ViewState =
  createAnchor(scope, customKey).anchor

/**
 * Convenience extension to get a [NativeStateFlow] wrapper for the view state.
 *
 * Use this from iOS to collect state updates via callbacks. Its collectors are not tied to an [AnchorContainer], so
 * cancel each yourself; [AnchorContainer.collectState] collectors stop on [AnchorContainer.clear].
 */
public fun <R : Effect, S : ViewState> AnchorSink<R, S, *>.nativeViewState(): NativeStateFlow<S> =
  NativeStateFlow(viewState)

/**
 * Convenience extension to get a [NativeSharedFlow] wrapper for signals.
 *
 * Use this from iOS to collect signal emissions via callbacks.
 *
 * Its collector accepts every signal. Each `collect` first receives, once, the signals held while no collector was
 * attached (for example one posted from `init` before this call), then live ones. Delivered signals are never
 * replayed. See [AnchorSink.signals] for the full delivery contract.
 *
 * Its collectors are not tied to an [AnchorContainer], so cancel each yourself; [AnchorContainer.collectSignals]
 * collectors stop on [AnchorContainer.clear].
 */
public fun <R : Effect, S : ViewState> AnchorSink<R, S, *>.nativeSignals(): NativeSharedFlow<SignalProvider> =
  NativeSharedFlow(signals)
