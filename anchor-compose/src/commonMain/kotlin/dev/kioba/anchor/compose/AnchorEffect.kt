package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

/**
 * Launches a side-effecting Anchor action when entering the composition.
 *
 * Use this within a [RememberAnchor] scope to replace the manual
 * `LaunchedEffect(Unit) { scope.execute { ... } }` pattern when you need to run
 * an action with the current Anchor as receiver. The action restarts whenever
 * any of the provided [keys] change, following the same semantics as
 * [LaunchedEffect].
 *
 * The Anchor type is automatically inferred from the action function's receiver
 * type, providing compile-time type safety.
 *
 * @param A The Anchor type, automatically inferred from the block parameter
 * @param keys Keys that, when changed, will restart the effect. Defaults to
 *        `arrayOf(Unit)` so the effect runs once when entering the composition.
 * @param block The action to execute. This is typically a function reference to
 *        an action defined as an extension on your Anchor type.
 *
 * @sample
 * ```kotlin
 * @Composable
 * fun CounterScreen() {
 *   RememberAnchor(scope = { counterAnchor() }) { state ->
 *     AnchorEffect(block = CounterAnchor::load)
 *   }
 * }
 * ```
 *
 * @see RememberAnchor For setting up the Anchor scope
 * @see anchor For creating event-driven action callbacks
 */
@Composable
public fun <A> AnchorEffect(
  vararg keys: Any? = arrayOf(Unit),
  block: suspend A.() -> Unit,
)
  where A : Anchor<out Effect, out ViewState, *> {
  val scope = LocalAnchor.current
  val updatedBlock = rememberUpdatedState(block)
  LaunchedEffect(*keys) {
    scope.execute {
      @Suppress("UNCHECKED_CAST")
      updatedBlock.value(this as A)
    }
  }
}
