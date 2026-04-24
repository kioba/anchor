package dev.kioba.anchor.internal

import kotlin.coroutines.cancellation.CancellationException

@PublishedApi
internal actual fun Throwable.isNonFatal(): Boolean =
  this !is CancellationException
