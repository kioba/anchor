package dev.kioba.anchor

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import dev.kioba.anchor.internal.AnchorRuntime
import dev.kioba.anchor.viewmodel.ContainerViewModel
import dev.kioba.anchor.viewmodel.containerViewModelFactory
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Owns one [Anchor] on iOS, from [createAnchor] until [clear].
 *
 * While you hold the container, its [anchor] stays the same instance: it keeps its state, and its `init` and
 * subscriptions keep running. [clear] stops them, together with every collector started through [collectState]
 * and [collectSignals]. Nothing else does, so a container that is dropped without [clear] leaks its anchor and
 * everything the anchor observes.
 *
 * Create one container per logical screen and hold it in the object that owns the screen, such as an
 * `ObservableObject` kept in `@StateObject`. That object owns the container and must call [clear] from its teardown:
 * `deinit`, or `.onDisappear` for a screen that should stop while hidden. The sample app's
 * `iosApp/iosApp/ViewModelProtocol.swift` shows the pattern.
 *
 * @param E The [Effect] type.
 * @param S The [ViewState] type.
 */
public class AnchorContainer<E, S> internal constructor(
  private val store: ViewModelStore,
  private val viewModel: ContainerViewModel<E, S, *>,
) where E : Effect, S : ViewState {
  /**
   * The anchor this container owns. Call actions on it.
   *
   * Actions called from Swift run in the calling `Task`, so [clear] does not cancel them.
   */
  public val anchor: Anchor<E, S, *>
    get() = viewModel.anchor

  /**
   * The current state. Use it to seed your own copy before the first [collectState] callback arrives.
   */
  public val state: S
    get() = viewModel.viewState.value

  /**
   * Calls [onEach] on the main thread with the current state, then with every update, until the returned handle
   * is cancelled or the container is cleared.
   *
   * Capture the owner weakly in [onEach] (`[weak self]`). The collector keeps [onEach] alive until it stops, so a
   * strong capture keeps the owner alive too, and its `deinit` never calls [clear].
   *
   * @param onEach Called for each state.
   * @return A [NativeCancellable] to stop this collector before [clear].
   */
  public fun collectState(
    onEach: (S) -> Unit,
  ): NativeCancellable =
    collect(viewModel.viewState, onEach)

  /**
   * Calls [onEach] on the main thread with every signal the anchor posts, until the returned handle is cancelled
   * or the container is cleared.
   *
   * Delivery is the same as [nativeSignals]: the collector accepts every signal, so its first call receives, once,
   * the signals held while no collector was attached (for example one posted from `init`), then live ones.
   * Delivered signals are never replayed. See [AnchorSink.signals] for the full delivery contract.
   *
   * Capture the owner weakly in [onEach] (`[weak self]`). The collector keeps [onEach] alive until it stops, so a
   * strong capture keeps the owner alive too, and its `deinit` never calls [clear].
   *
   * @param onEach Called for each signal.
   * @return A [NativeCancellable] to stop this collector before [clear].
   */
  public fun collectSignals(
    onEach: (SignalProvider) -> Unit,
  ): NativeCancellable =
    collect(viewModel.signals, onEach)

  /**
   * Releases the anchor: clears the backing [ViewModelStore], which cancels the ViewModel scope and with it the
   * anchor's `init`, its subscriptions, and every collector started through this container.
   *
   * Call it from the teardown of the object that owns the container (`deinit` or `.onDisappear`). Calling it again
   * does nothing. Afterwards [anchor] still accepts actions, but nothing observes them: collectors started later
   * never call back.
   */
  public fun clear() {
    store.clear()
  }

  private fun <T> collect(
    flow: Flow<T>,
    onEach: (T) -> Unit,
  ): NativeCancellable {
    // A child of the ViewModel scope, so clear() cancels it; a no-op once cleared.
    val job = viewModel.viewModelScope.launch(Dispatchers.Main + containment) { flow.collect { onEach(it) } }
    return NativeCancellable { job.cancel() }
  }

  private companion object {
    // Kotlin/Native aborts the process on an uncaught coroutine exception. A collector whose callback throws
    // stops; the rest of the container is unaffected.
    val containment = CoroutineExceptionHandler { _, _ -> }
  }
}

/**
 * Creates an [Anchor] on iOS and returns the [AnchorContainer] that owns it.
 *
 * The anchor runs in its own ViewModel: its subscriptions start, then its `init` runs, as with `RememberAnchor` on
 * Android. It lives until [AnchorContainer.clear]. Each call creates a new, independent anchor; nothing is shared
 * or looked up between calls, so create one container per logical screen and hold it. The caller owns the container
 * and must call [AnchorContainer.clear] from its teardown.
 *
 * @param S The [ViewState] type.
 * @param E The [Effect] type.
 * @param scope Factory function that creates the [Anchor] instance.
 * @param customKey Optional key the anchor is stored under in the container's own store. Every container has its
 *   own store, so the key never shares an anchor between containers.
 * @return The [AnchorContainer] that owns the new anchor.
 */
@Suppress("UNCHECKED_CAST")
public fun <S, E> createAnchor(
  scope: (RememberAnchorScope) -> Anchor<E, S, *>,
  customKey: String? = null,
): AnchorContainer<E, S>
  where
        E : Effect,
        S : ViewState {
  val storeOwner =
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  val factory = containerViewModelFactory { scope(AnchorRuntimeScope) as AnchorRuntime<E, S, *> }
  val provider = ViewModelProvider.create(storeOwner, factory)
  val viewModel =
    when {
      customKey != null -> provider[customKey, ContainerViewModel::class]
      else -> provider[ContainerViewModel::class]
    } as ContainerViewModel<E, S, *>

  return AnchorContainer(storeOwner.viewModelStore, viewModel)
}
