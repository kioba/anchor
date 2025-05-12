package dev.kioba.anchor.features.config.model

import androidx.compose.runtime.Immutable
import dev.kioba.anchor.ViewState

@Immutable
 public data class ConfigState(
  val text: String? = null,
 ) : ViewState

