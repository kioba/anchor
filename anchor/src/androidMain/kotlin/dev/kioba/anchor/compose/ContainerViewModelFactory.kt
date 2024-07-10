package dev.kioba.anchor.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras

@PublishedApi
internal fun interface ContainerViewModelFactory : ViewModelProvider.Factory {
  fun factory(): ViewModel

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(
    modelClass: Class<T>,
  ): T =
    factory() as T

  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(
    modelClass: Class<T>, extras: CreationExtras,
  ): T =
    factory() as T

}
