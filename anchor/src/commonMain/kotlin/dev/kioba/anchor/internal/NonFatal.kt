package dev.kioba.anchor.internal

/**
 * Returns `true` when this [Throwable] is non-fatal and can safely be
 * caught by application-level error handlers.
 *
 * Fatal exceptions ([kotlin.coroutines.cancellation.CancellationException],
 * and on JVM: VirtualMachineError, InterruptedException, LinkageError) must always propagate.
 */
@PublishedApi
internal expect fun Throwable.isNonFatal(): Boolean
