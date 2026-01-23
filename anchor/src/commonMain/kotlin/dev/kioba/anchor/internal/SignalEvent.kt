package dev.kioba.anchor.internal

import dev.kioba.anchor.Signal
import kotlinx.atomicfu.atomic

@PublishedApi
internal data class SignalEvent(
  val signal: Signal,
  val id: Long
)

private val signalIdCounter = atomic(0L)

internal fun nextSignalId(): Long = signalIdCounter.incrementAndGet()
