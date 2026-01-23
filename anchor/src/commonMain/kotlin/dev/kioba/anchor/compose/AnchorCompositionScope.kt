package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.kioba.anchor.ViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * AnchorCompositionScope provides access to the state and actions of an Anchor.
 */
public interface AnchorCompositionScope<S : ViewState> {
  /**
   * Returns the current state. Accessing this property will cause the calling
   * composable to recompose whenever any part of the state changes.
   */
  public val state: S
    @Composable get

  /**
   * Collects a specific part of the state. Recomposes only when the selected
   * value changes.
   */
  @Composable
  public fun <T> collectState(selector: (S) -> T): T
}

@PublishedApi
internal class AnchorCompositionScopeImpl<S : ViewState>(
  private val stateFlow: StateFlow<S>,
) : AnchorCompositionScope<S> {

  override val state: S
    @Composable get() = stateFlow.collectAsState(Dispatchers.Main.immediate).value

  @Composable
  override fun <T> collectState(selector: (S) -> T): T {
    val selection by remember(stateFlow, selector) {
      stateFlow.map { selector(it) }.distinctUntilChanged()
    }.collectAsState(selector(stateFlow.value), Dispatchers.Main.immediate)
    return selection
  }
}
