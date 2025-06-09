package dev.kioba.anchor

@PublishedApi
internal fun interface SignalProvider {
  fun provide(): Signal
}
