package dev.kioba.anchor.features.config.model

public sealed interface ConfigError {
  public data object EmptyInput : ConfigError

  public data class TooLong(val maxLength: Int) : ConfigError
}
