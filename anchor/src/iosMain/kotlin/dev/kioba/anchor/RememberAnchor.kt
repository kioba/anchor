package dev.kioba.anchor

import kotlinx.coroutines.CoroutineScope

public inline fun <reified S, E> rememberAnchor(
  noinline scope: RememberAnchorScope.() -> Anchor<E, S>,
  customKey: String? = null,
  crossinline content: (S) -> Unit,
) where E : Effect, S : ViewState {
  val anchorScope: ContainedScope<AnchorRuntime<E, S>, E, S> =
    ContainerViewModel(AnchorRuntimeScope.scope() as AnchorRuntime<E, S>, Dispatchers.Main.immediate)

//      customKey ?: S::class.qualifiedName.orEmpty()

  val state by anchorScope.collectViewState()
  val signal by anchorScope.collectSignal()

  content(state)
}

@PublishedApi
internal class ContainerViewModel<E, S>(
  override val anchor: AnchorRuntime<E, S>,
  override val coroutineScope: CoroutineScope
) : ContainedScope<AnchorRuntime<E, S>, E, S>
  where E : Effect, S : ViewState
