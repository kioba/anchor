package dev.kioba.anchor.internal

import kotlin.coroutines.cancellation.CancellationException

@PublishedApi
internal actual fun Throwable.isNonFatal(): Boolean =
  when (this) {
    is VirtualMachineError,
    is InterruptedException,
    is LinkageError,
    is CancellationException,
    -> false

    else -> true
  }
