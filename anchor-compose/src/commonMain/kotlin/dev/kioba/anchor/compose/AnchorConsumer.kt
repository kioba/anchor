package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import dev.kioba.anchor.AnchorScope

/**
 * Exposes the current [AnchorScope] to non-composable callbacks.
 *
 * Use this when you need to invoke an Anchor action from a context that cannot directly
 * call the [anchor] composable, such as the `onClick` body of an `AndroidView` or other
 * interop layers that take plain Kotlin callbacks.
 *
 * @param content Receives the [AnchorScope] provided by the nearest [RememberAnchor]
 *        in the composition tree.
 *
 * @sample
 * ```kotlin
 * @Composable
 * fun CounterScreen() {
 *   RememberAnchor(scope = { counterAnchor() }) { state ->
 *     AnchorConsumer { scope ->
 *       AndroidView(
 *         factory = { context ->
 *           Button(context).apply {
 *             setOnClickListener {
 *               scope.execute { (this as CounterAnchor).increment() }
 *             }
 *           }
 *         },
 *       )
 *     }
 *   }
 * }
 * ```
 *
 * @see anchor For the preferred way to create action callbacks in pure Compose code
 * @see RememberAnchor For providing the AnchorScope
 */
@Composable
public fun AnchorConsumer(
  content: @Composable (AnchorScope<*, *>) -> Unit,
) {
  content(LocalAnchor.current)
}
