package dev.kioba.anchor.viewmodel

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.AnchorRuntimeScope
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime

@PublishedApi
internal fun <R, A, S, Err> containerViewModelFactory(
  factory: () -> A
): ContainerViewModelFactory where R : Effect, A : AnchorRuntime<R, S, Err>, S : ViewState, Err : Any =
  ContainerViewModelFactory { ContainerViewModel(factory()) }

public fun <R : Effect, S : ViewState> anchorContainerViewModelFactory(
  scope: RememberAnchorScope.() -> Anchor<R, S, *>,
): ContainerViewModelFactory =
  containerViewModelFactory { AnchorRuntimeScope.scope() as AnchorRuntime<R, S, *> }
