# Spec 026: `cancellable(key)` must not hold the global jobs mutex across `cancelAndJoin`, must stay exclusive per key, and must not leave stale entries

- **Source issue**: #145 (umbrella), children #129 (race) and #130 (leak). Both were closed COMPLETED 2025-12-26 by PR #147 (`8f361aa`). The reported scenarios are fixed. Three residuals remain.
- **Severity**: P2. The most common symptom is a latency coupling across unrelated keys that users hit with blocking effects. The per-key exclusivity breach and the leak need rarer interleavings. Workaround: keep effects cancellation-cooperative (`runInterruptible`, suspend clients).
- **Verified at**: origin/master `0bc430c`, 2026-09-29
- **Plan**: [plans/026-cancellable-key-isolation.md](../026-cancellable-key-isolation.md). It uses Investigate-then-Act gating: core concurrency, risk MED.
- **Umbrella table**: see [025 §7](025-collectstate-granular-recomposition.md)

## 1. Problem statement

`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:181-219`:

```kotlin
    coroutineScope {
      var raised: RaisedException? = null

      val jobToWait =
        jobsMutex.withLock {
          // Cancel and remove old job if it exists
          val oldJob = jobs.remove(key)
          oldJob?.cancelAndJoin()

          // Create new job (don't wait while holding lock!)
          val newJob =
            launch {
              try {
                block()
              } catch (e: RaisedException) {
                raised = e
                throw e
              } finally {
                // Clean up completed job to prevent memory leak
                // Only remove if this job is still the current one for this key
                jobsMutex.withLock {
                  if (jobs[key] === coroutineContext[Job]) {
                    jobs.remove(key)
                  }
                }
              }
            }

          // Store the new job while still holding the lock
          newJob.also { jobs[key] = it }
        }
```

There is one `jobsMutex` per anchor. It is held **while joining** the previous job for the key. That causes three problems:

1. **Cross-key stall.** Suppose the previous job does not respond to cancellation promptly, for example a blocking client call inside `effect {}`. `effect` defaults to `Dispatchers.IO` (`Anchor.kt:196-199`), which invites blocking calls. Then every `cancellable` call on **any other key** in the same anchor waits for it.
2. **Stale entry (the #130 residual).** A job can be cancelled from outside (its caller cancelled) while another key holds the mutex. Its `finally` then calls `jobsMutex.withLock`, which suspends on the held lock, is cancelled, and throws `CancellationException` before `jobs.remove(key)`. The cancelled `Job` stays in the map. The design depends on this throw: making the cleanup `NonCancellable` would deadlock against the joiner that holds the lock.
3. **Per-key overlap (the #129 residual).** Caller B removes A from the map before joining it. If B is cancelled while joining, B's `withLock` releases and A is no longer in the map. A third call C therefore sees no predecessor and starts while A is still running.

### Evidence (behavioral, desktop JVM, disposable worktree at `0bc430c`)

| Test (regression suite in the plan) | master `0bc430c` | proposed design |
|--------------------------------------|------------------|-----------------|
| 200 concurrent same-key calls: max concurrent blocks | 1 (PASS) | 1 (PASS) |
| 1,000 unique keys: residual entries | 0 (PASS) | 0 (PASS) |
| k1's caller cancelled while k2's re-entry holds the mutex: entries left | **`[k1]` (FAIL)** | `{}` (PASS) |
| `search` blocked in `effect { Thread.sleep(400) }` and re-issued; then `refresh` entry latency | **352 ms (FAIL)** | 0 ms (PASS) |
| Same, with a `NonCancellable` 400 ms cleanup instead of blocking | **342-360 ms (FAIL)** | 1-4 ms (PASS) |
| Queued caller B cancelled behind blocking A, then C issued: max concurrent for key | **2 (FAIL)** | 1 (PASS) |
| Cancelling a queued caller (B) returns promptly | 0-1 ms (PASS) | 0 ms (PASS) |

With the exact §3 code, the full `:anchor:desktopTest` suite passes: 97/97, including 90 existing tests. `:anchor:iosSimulatorArm64Test` also passes all 86 existing `commonTest` tests on Kotlin/Native.

## 2. Goals / non-goals

**Goals**
- G1: At most one `block` per key runs at a time, including when callers are cancelled. The next block starts only after every predecessor for that key has **completed**, not merely been cancelled. This keeps today's KDoc promise (`Anchor.kt:212-214`, `AnchorRuntime.kt:159-170`).
- G2: A slow or non-cooperative job on key X never delays `cancellable` entry on key Y.
- G3: `jobs` never retains an entry after its whole predecessor chain has completed, observed at the next `cancellable` call.
- G4: Cancelling a caller that is queued behind a slow predecessor returns promptly (no regression).
- G5: `RaisedException` propagation stays unchanged (`AnchorRuntime.kt:195-218`, `ExecuteBoundaryTest`).

**Non-goals**
- Changing `cancellable`'s public signature or KDoc semantics.
- Interrupting blocking code. Anchor cannot do that; `runInterruptible` stays the user's tool. A docs note is included.
- `anchor-test`'s `AnchorTestRuntime`, which has a separate implementation. Plan 014 covers virtual-time tests.

## 3. Proposed design: per-key completion chain, and never join under the mutex

Each map entry carries the job plus a `done` Job. `done` completes only when this job **and** the previous entry's `done` have completed. Callers wait on the chain, never while holding the mutex.

```kotlin
@PublishedApi
internal class KeyedJob(val job: Job, val done: Job)

// inside cancellable(key, block):
coroutineScope {
  var raised: RaisedException? = null
  val jobToWait =
    jobsMutex.withLock {                                   // only non-suspending work under the lock
      jobs.entries.removeAll { it.value.done.isCompleted } // G3: purge leftovers
      val previous = jobs[key]
      previous?.job?.cancel()                              // signal only; no join here (G2)
      val newJob =
        launch {
          val self = coroutineContext[Job]
          try {
            previous?.done?.join()                         // cancellable wait for the whole chain (G1, G4)
            block()
          } catch (e: RaisedException) {
            raised = e
            throw e
          } finally {
            withContext(NonCancellable) {                  // safe: the lock is never held across a join
              jobsMutex.withLock {
                val predecessorDone = previous?.done?.isCompleted ?: true
                if (jobs[key]?.job === self && predecessorDone) jobs.remove(key)
              }
            }
          }
        }
      val done = Job()
      newJob.invokeOnCompletion {
        val prevDone = previous?.done
        if (prevDone == null) done.complete() else prevDone.invokeOnCompletion { done.complete() }
      }
      jobs[key] = KeyedJob(newJob, done)
      newJob
    }
  jobToWait.join()
  raised?.let { throw it }
}
```

Why it holds:
- G1: a successor joins `previous.done`, which transitively waits for every predecessor. A job cancelled before or during its wait still completes `done` only after its predecessor's `done`, so the chain cannot be skipped. `finally` removes the entry only when the predecessor chain is done. Otherwise the entry stays for the next caller to join, and it is purged later.
- G2: under the lock there are only map operations, `cancel()`, `launch` (DEFAULT start, so no body runs inline) and `invokeOnCompletion`.
- No deadlock: the `NonCancellable` cleanup waits only for the short, non-suspending critical sections.
- Kotlin/Native: no fire-and-forget coroutine is added. `done` is a plain `Job()`, and the handlers do not throw. The Kotlin/Native gotcha about SupervisorJob without a handler therefore does not apply.

### Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| C1: keep the current structure and add only the purge (`jobs.entries.removeAll { it.value.isCompleted }` on acquire) | Fixes the stale entry (G3) only. The cross-key stall (G2) and the per-key overlap (G1 residual) remain. It is the fallback if the maintainer wants a minimal diff. |
| Wait for the predecessor in `withContext(NonCancellable)` inside the new job | Prototyped. It fixes G1-G3, but a cancelled queued caller then waits ~359 ms for the predecessor, which regresses G4 (test: "cancelling a queued caller took 359ms"). |
| `CoroutineStart.LAZY` new job, with `previous.cancelAndJoin()` in the caller outside the lock, then `start()` | Breaks the chain. If a lazy job is cancelled before `start()`, it completes immediately without awaiting its predecessor, so a third call overlaps. |
| Per-key `Mutex` map | It still needs cleanup of the mutex map, which is the same leak problem. Joining under a per-key lock keeps the cancelled-waiter overlap (G1 residual). |
| `NonCancellable` cleanup with the current join-under-lock | Deadlocks: the joiner holds the lock that the cancelled job's cleanup waits for. |

## 4. Behavior and API changes

- **Non-breaking.** `cancellable`'s signature and KDoc contract are unchanged.
- `jobs` changes type from `MutableMap<Any, Job>` to `MutableMap<Any, KeyedJob>`. It is `@PublishedApi internal`, and no inline function references it (`git grep "\.jobs\b"`: tests only). The tests use only `.size`, `.isEmpty()` and `.keys`, so they compile unchanged.
- Observable changes:
  - An unrelated key no longer waits on a slow job.
  - A new block no longer starts while a cancelled predecessor is still running.
  - A leftover entry is purged on the next call.
- KDoc addition: "Cancellation is cooperative. A predecessor stuck in blocking code delays only the next block for the same key. Wrap blocking calls in `runInterruptible` to make them cancellable."

## 5. Acceptance criteria

1. The seven regression tests in the plan all pass. The four marked FAIL above fail on `0bc430c` first.
2. `./gradlew :anchor:desktopTest` and `./gradlew :anchor:iosSimulatorArm64Test` are all green, with no change to existing tests.
3. The `cancellable` KDoc in `AnchorRuntime.kt` and `Anchor.kt` states the cooperative-cancellation note. The "Thread-safe"/"Memory-safe" claims stay accurate.
4. `./gradlew build` exits 0.

## 6. Open questions for the maintainer

1. GO on the chain design (C2), or the minimal purge (C1)?
2. The regression tests use real threads and `Thread.sleep` (JVM-only), so they live in `anchor/src/desktopTest/`. Plan 014 prefers virtual time for cancellable tests. Keep these as the documented real-parallelism exceptions?
3. Should the docs (#140 / `docs/concepts.md`) state the per-key guarantee explicitly: "the next block starts after the previous one has completed"?
