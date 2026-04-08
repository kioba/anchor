package dev.kioba.anchor.viewmodel

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorRuntimeScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime

@PublishedApi
internal fun <E, R, S> containerViewModelFactory(
  factory: () -> R
): ContainerViewModelFactory where E : Effect, R : AnchorRuntime<E, S>, S : ViewState =
  ContainerViewModelFactory { ContainerViewModel(factory()) }

public fun <E : Effect, S : ViewState> anchorContainerViewModelFactory(
  scope: RememberAnchorScope.() -> Anchor<E, S>,
): ContainerViewModelFactory =
  containerViewModelFactory { AnchorRuntimeScope.scope() as AnchorRuntime<E, S> }
