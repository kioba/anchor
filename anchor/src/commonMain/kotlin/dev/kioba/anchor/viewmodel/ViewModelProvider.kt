package dev.kioba.anchor.viewmodel

import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.internal.AnchorRuntime

@PublishedApi
internal fun <E, R, S> containerViewModelFactory(
  factory: () -> R
): ContainerViewModelFactory where E : Effect, R : AnchorRuntime<E, S>, S : ViewState =
  ContainerViewModelFactory { ContainerViewModel(factory()) }
