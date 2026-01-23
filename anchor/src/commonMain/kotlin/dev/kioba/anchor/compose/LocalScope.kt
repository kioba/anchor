package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

/**
 * Internal CompositionLocal providing access to the current AnchorScope.
 *
 * This is used internally by [anchor] composable functions to access the Anchor instance
 * provided by the nearest [RememberAnchor] in the composition tree.
 *
 * Users should not access this directly. Instead, use the [anchor] functions to create
 * action callbacks within a [RememberAnchor] scope.
 *
 * The default value is a no-op implementation used for Compose previews and testing.
 *
 * @see dev.kioba.anchor.compose.anchor For creating action callbacks
 * @see dev.kioba.anchor.compose.RememberAnchor For providing the AnchorScope
 */
@PublishedApi
internal val LocalAnchor: ProvidableCompositionLocal<AnchorScope<*, *>> =
  staticCompositionLocalOf {
    AnchorScope<Effect, ViewState> { _ ->
      // No-op default implementation for preview/testing
    }
  }
