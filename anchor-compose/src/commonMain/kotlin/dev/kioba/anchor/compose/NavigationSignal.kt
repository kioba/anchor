package dev.kioba.anchor.compose

import dev.kioba.anchor.Signal

/**
 * Opt-in convention for common navigation [Signal]s emitted from an Anchor.
 *
 * Provides ready-made variants for "navigate back" and "navigate back with a result",
 * without coupling Anchor to any specific navigation framework. The type parameter on
 * [BackWith] is named `T` rather than `R` to avoid collision with the conventional
 * `R : Effect` type parameter used throughout the Anchor DSL.
 *
 * @sample
 * ```kotlin
 * suspend fun DetailsAnchor.close() {
 *   post { NavigationSignal.Back }
 * }
 *
 * suspend fun DetailsAnchor.submit(id: Long) {
 *   post { NavigationSignal.BackWith(id) }
 * }
 * ```
 */
public interface NavigationSignal : Signal {
  /**
   * Signal requesting a plain "navigate back" with no payload.
   */
  public data object Back : NavigationSignal

  /**
   * Signal requesting a "navigate back" carrying a [result] for the previous destination.
   *
   * @param result The value to deliver back to the previous destination.
   */
  public data class BackWith<T>(public val result: T) : NavigationSignal
}
