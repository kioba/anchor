package dev.kioba.anchor.features.main.model

import dev.kioba.anchor.Event

internal sealed interface MainEvent : Event {
  data object Refresh : MainEvent

  data object Cancel : MainEvent
}
