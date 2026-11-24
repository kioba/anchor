package dev.kioba.anchor.internal

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Collects every flow in its own coroutine, started undispatched in list order,
 * and returns once all of them have completed.
 *
 * Undispatched: each flow runs until it first suspends before the next one
 * starts, so a flow that collects its source directly is already reading when
 * the flows after it start.
 *
 * A failure ends only the flow that threw. Children of a supervisor handle
 * their own failures, so each one carries a handler. Without it, an exception a
 * flow rethrows past `safeExecute` (no `defect` configured) reaches each
 * platform's default uncaught-exception path: desktop JVM logs it, Android
 * crashes the app, Kotlin/Native aborts the process. The handler makes a failed
 * flow end quietly on every platform.
 */
internal suspend fun List<Flow<*>>.collectSupervised(): Unit =
  supervisorScope {
    val containment = CoroutineExceptionHandler { _, _ -> }
    for (flow in this@collectSupervised) {
      launch(containment, start = CoroutineStart.UNDISPATCHED) { flow.collect() }
    }
  }
