package dev.kioba.anchor.internal

import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Signal delivery with hold-until-accepted semantics.
 *
 * A posted signal goes live to every attached collector that accepts it. If none does, it is held (at most
 * [capacity], oldest dropped) and handed once to the first accepting collector that attaches. Delivered signals are
 * never replayed.
 *
 * [signals] serves accept-all collectors and [signalsMatching] type-filtered ones. One lock guards the held
 * signals, the typed acceptors and the live-or-hold decision, so a signal is either held or live, never both. The
 * lock is never held while suspended: a collector's slot is registered before it asks for the lock to drain, so a
 * [post] that waited on a full live buffer while holding the lock could be waiting on that very collector.
 *
 * Launches no coroutines of its own (Kotlin/Native: no unsupervised failures).
 */
internal class SignalBus(
  private val capacity: Int = HELD_CAPACITY,
) {
  private val lock = Mutex()
  private val held = ArrayDeque<SignalProvider>()
  private val typedAcceptors = mutableListOf<(Signal) -> Boolean>()
  private val raw = MutableSharedFlow<SignalProvider>(extraBufferCapacity = capacity)
  private val typed = MutableSharedFlow<SignalProvider>(extraBufferCapacity = capacity)

  /**
   * Delivers [provider] to the attached collectors that accept it, or holds it if none does. Suspends only while
   * an accepting collector's buffer is full.
   */
  suspend fun post(
    provider: SignalProvider,
  ) {
    val signal = provider.provide()
    var toRaw = false
    var toTyped = false
    lock.withLock {
      toRaw = raw.subscriptionCount.value > 0
      toTyped = typedAcceptors.any { accepts -> accepts(signal) }
      if (!toRaw && !toTyped) {
        if (held.size == capacity) held.removeFirst()
        held.addLast(provider)
      }
    }
    // The collectors counted above are subscribed already, so emitting after the lock still reaches them.
    if (toRaw) raw.emit(provider)
    if (toTyped) typed.emit(provider)
  }

  /** Accept-all stream; the first collector drains everything held. */
  val signals: SharedFlow<SignalProvider> =
    raw.asSharedFlow().onSubscription {
      val drained = lock.withLock { held.toList().also { held.clear() } }
      drained.forEach { emit(it) }
    }

  /** Type-filtered stream; drains only held signals it [accepts]. */
  fun signalsMatching(
    accepts: (Signal) -> Boolean,
  ): Flow<SignalProvider> =
    flow {
      var registered = false
      try {
        typed
          .onSubscription {
            val drained =
              lock.withLock {
                typedAcceptors += accepts
                registered = true
                val (match, rest) = held.partition { accepts(it.provide()) }
                held.clear()
                held.addAll(rest)
                match
              }
            drained.forEach { emit(it) }
          }.filter { accepts(it.provide()) }
          .collect { emit(it) }
      } finally {
        // Only this collection's own entry: another collector may have registered an equal acceptor.
        if (registered) {
          withContext(NonCancellable) { lock.withLock { typedAcceptors.remove(accepts) } }
        }
      }
    }

  internal companion object {
    const val HELD_CAPACITY: Int = 64
  }
}
