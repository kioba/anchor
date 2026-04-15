package dev.kioba.anchor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A cancellable handle for stopping Flow collection from iOS.
 */
public fun interface NativeCancellable {
  public fun cancel()
}

/**
 * iOS-friendly wrapper around Kotlin [StateFlow].
 *
 * Provides callback-based collection since Swift cannot directly
 * iterate Kotlin Flows without SKIE.
 *
 * @param T The type of values emitted by the flow.
 */
public class NativeStateFlow<T : Any>(
  private val flow: StateFlow<T>,
) {
  /**
   * The current value of the [StateFlow].
   */
  public val value: T get() = flow.value

  /**
   * Starts collecting the [StateFlow] on the Main dispatcher.
   *
   * @param onEach Called for each emitted value.
   * @return A [NativeCancellable] to stop collection.
   */
  public fun collect(onEach: (T) -> Unit): NativeCancellable {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    scope.launch { flow.collect { onEach(it) } }
    return NativeCancellable { scope.cancel() }
  }
}

/**
 * iOS-friendly wrapper around Kotlin [SharedFlow].
 *
 * Provides callback-based collection since Swift cannot directly
 * iterate Kotlin Flows without SKIE.
 *
 * @param T The type of values emitted by the flow.
 */
public class NativeSharedFlow<T : Any>(
  private val flow: SharedFlow<T>,
) {
  /**
   * Starts collecting the [SharedFlow] on the Main dispatcher.
   *
   * @param onEach Called for each emitted value.
   * @return A [NativeCancellable] to stop collection.
   */
  public fun collect(onEach: (T) -> Unit): NativeCancellable {
    val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    scope.launch { flow.collect { onEach(it) } }
    return NativeCancellable { scope.cancel() }
  }
}
