package dev.kioba.anchor.compose

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.AnchorScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import kotlin.reflect.KClass

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

/**
 * Internal CompositionLocal mapping each enclosing Anchor's [ViewState] class to its AnchorScope.
 *
 * Every [RememberAnchor] provides its parent's map plus its own `S::class` entry, so nested
 * anchors accumulate and the nearest anchor for a given [ViewState] wins. [PreviewAnchor]
 * registers a no-op scope for its `S`. The [anchor] functions resolve their target here by
 * the ViewState of the action's receiver.
 *
 * Users should not access this directly.
 *
 * @see dev.kioba.anchor.compose.anchor For creating action callbacks
 * @see dev.kioba.anchor.compose.RememberAnchor For providing the AnchorScope
 */
@PublishedApi
internal val LocalAnchors: ProvidableCompositionLocal<Map<KClass<*>, AnchorScope<*, *>>> =
  staticCompositionLocalOf { emptyMap() }
