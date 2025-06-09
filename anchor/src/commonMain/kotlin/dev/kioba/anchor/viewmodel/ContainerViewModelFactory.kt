package dev.kioba.anchor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

public fun interface ContainerViewModelFactory
  : ViewModelProvider.Factory {

  public fun factory(): ViewModel

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(
    modelClass: KClass<T>,
    extras: CreationExtras,
  ): T =
    factory() as T
}
