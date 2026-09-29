# Plan 026: Investigate-and-decide — per-key completion chain for `cancellable` (no join under the global mutex)

> **Executor instructions**: This is an INVESTIGATE-THEN-ACT plan with a hard
> decision gate. Complete Phase A (investigation), write the findings into
> this file under "Investigation results", and STOP for maintainer review
> before executing Phase B. Do not perform Phase B in the same run unless the
> operator explicitly pre-authorized it *and* named the option (C2 chain, or
> C1 purge-only). Follow every step and run every verification command. If
> anything in "STOP conditions" occurs, stop and report. When done (either
> phase), update the status row for this plan in `plans/README.md`, unless a
> reviewer dispatched you and told you they maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt anchor/src/commonTest/kotlin/dev/kioba/anchor/ anchor/build.gradle.kts`
>
> Expected benign drift in `AnchorRuntime.kt` outside `cancellable()`:
> - plan 024: signals wiring;
> - plans 003/004: `subscribe()`;
> - plan 007: `@OptIn` annotations.
>
> Plan 014 may have migrated the existing cancellable tests to virtual time. If so, re-read them, because they must still pass.
>
> Any change inside `cancellable()` (`AnchorRuntime.kt:157-220` at `0bc430c`) is a STOP.

## Status

- **Priority**: P2
- **Effort**: M (A: S, B: S-M)
- **Risk**: MED. The change is in core concurrency. It is mitigated by a prototype that passed 97/97 desktop and 86/86 iOS-simulator tests.
- **Depends on**: none. Complements plan 014 (these tests are real-parallelism exceptions to its virtual-time migration).
- **Category**: bug
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: [plans/specs/026-cancellable-key-isolation.md](specs/026-cancellable-key-isolation.md)

## Why this matters

PR #147 fixed the race and leak that #129 and #130 reported. It did so by holding one per-anchor
`jobsMutex` **while** `cancelAndJoin`-ing the previous job. On `0bc430c` that leaves three verified
residuals:

- A blocking `effect {}` under `cancellable("search")` stalls `cancellable("refresh")` for **352 ms**. `effect` defaults to `Dispatchers.IO`, which invites blocking calls.
- A job cancelled during lock contention leaves a **stale map entry** (`[k1]`).
- A queued caller cancelled behind a non-cooperative predecessor lets a third call **overlap** that predecessor (max concurrency 2 for one key). That breaks the core `cancellable` guarantee.

## Current state

`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt`:

- `:66-77`:
  - `@PublishedApi internal val jobs: MutableMap<Any, Job> = mutableMapOf()`
  - `private val jobsMutex = Mutex()`
- `:157-220`: KDoc ("Thread-safe … Memory-safe: Completed jobs are automatically cleaned up … in all scenarios") and the body:

  ```kotlin
    override suspend fun cancellable(
      key: Any,
      block: suspend Anchor<R, S, Err>.() -> Unit,
    ) {
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

        // Wait for the job to complete (outside the lock)
        jobToWait.join()

        // Propagate RaisedException after join — CancellationException semantics
        // cause join() to complete normally, but the domain error must propagate.
        raised?.let { throw it }
      }
    }
  ```

- Interface KDoc, `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:209-231`: "If a job with the same key is already running, it will be cancelled before the new block is executed."
- Tests that read `jobs`: `CancellableBasicTest.kt:73,115`, `JobIdentityTest.kt:71-77` and `JobIdentityEdgeCaseTest.kt:48-143`. They use `.size`, `.keys` and `.isEmpty()` only.
- `TestState` is `internal data class` in `anchor/src/commonTest/.../CancellableTest.kt:428`. It is visible from `desktopTest`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Scratch worktree (Phase A) | `git worktree add ../anchor-145c origin/master` | created |
| New tests | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.CancellableKeyIsolationTest'` | see steps |
| Module tests (JVM) | `./gradlew :anchor:desktopTest` | exit 0 |
| Module tests (Native) | `./gradlew :anchor:iosSimulatorArm64Test` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

If another Gradle process holds the build cache lock, add `--no-build-cache`.

## Scope

**Phase A**: scratch worktree only. The only in-repo change is appending "Investigation results" here.

**Phase B** (after GO):
- **In scope**:
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt`: `jobs`, `cancellable()`, its KDoc, and a new `KeyedJob` holder
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt`: the `CancellableAnchor.cancellable` KDoc only
  - `anchor/src/desktopTest/kotlin/dev/kioba/anchor/CancellableKeyIsolationTest.kt` (create)
- **Out of scope**:
  - the existing cancellable tests (they must pass unmodified);
  - `anchor-test`'s `AnchorTestRuntime`;
  - `subscribe()`, signals and `docs/`. A docs sentence for #140 is optional and goes to the maintainer; see spec Q3.

## Git workflow

- Branch: `fix/cancellable-key-isolation`
- Commits: `🧪 Add cancellable key-isolation regression tests`, then `🐛 Chain cancellable jobs per key instead of joining under the jobs mutex`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A — Investigation (always safe; scratch worktree)

**A1. Reproduce.** In the scratch worktree, create
`anchor/src/desktopTest/kotlin/dev/kioba/anchor/CancellableKeyIsolationTest.kt`
from the Test plan and run it.

**Verify**: exactly 4 failures on unmodified code. The messages were observed at `0bc430c` (timings vary by ±20 ms):
- `stale entries left in jobs map: [k1]`
- `unrelated key blocked for ~350ms`, twice
- `two blocks for key k ran at the same time expected:<1> but was:<2>`

The other 3 tests pass. If any of the 4 passes, STOP (drift).

**A2. Apply the C2 design** (spec §3) to `cancellable()`:
- Add `KeyedJob`.
- Change `jobs` to `MutableMap<Any, KeyedJob>`.
- Replace `cancelAndJoin` with `cancel()` plus the `done` chain.
- Keep the `RaisedException` comments.
- Keep imports sorted: add `NonCancellable`, remove `cancelAndJoin`.

**Verify**:
- `./gradlew :anchor:desktopTest` is all green (97 tests at `0bc430c`: 90 existing plus 7 new).
- `./gradlew :anchor:iosSimulatorArm64Test` is all green (86 at `0bc430c`).

**A3. Stress.** Run the new test class 20 times, one invocation per run:
`for i in $(seq 20); do ./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.CancellableKeyIsolationTest' --rerun -q || break; done`

**Verify**: 20/20 green. Record any flake with its test name.

**A4. Optional C1 comparison.** Apply only the purge line to unmodified code: `jobs.entries.removeAll { it.value.isCompleted }` at the top of the `withLock`. Record which of the 4 regressions it fixes. The expectation is that it fixes only the stale-entry test.

**A5. Write "Investigation results"**:
- A1-A4 outcomes, including timings;
- a C2/C1 recommendation;
- any deviation from the spec's code.

**STOP.** Phase B needs the maintainer's GO naming C2 or C1.

### Phase B — Execution (only after maintainer GO)

**B1 (test-first).** Add `CancellableKeyIsolationTest.kt` to the branch.

**Verify**: `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.CancellableKeyIsolationTest'` shows the same 4 failures as A1.

**B2. Implement the chosen option.** For C2, use this code, which is spec §3 verified at `0bc430c`:

```kotlin
  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    coroutineScope {
      var raised: RaisedException? = null

      val jobToWait =
        jobsMutex.withLock {
          // Only non-suspending work happens under the lock: never join here, or a
          // slow job on one key would stall every other key.
          jobs.entries.removeAll { it.value.done.isCompleted }
          val previous = jobs[key]
          previous?.job?.cancel()

          val newJob =
            launch {
              val self = coroutineContext[Job]
              try {
                // Wait until the previous holder of this key and all of its
                // predecessors have completed. Cancellable: a successor waits on
                // this job's `done`, which also waits for `previous.done`.
                previous?.done?.join()
                block()
              } catch (e: RaisedException) {
                raised = e
                throw e
              } finally {
                withContext(NonCancellable) {
                  jobsMutex.withLock {
                    // Remove only when still current AND the predecessor chain is
                    // done; otherwise the next caller must still wait on `done`.
                    // Leftovers are purged on the next acquisition.
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

      // Propagate RaisedException after join — CancellationException semantics
      // cause join() to complete normally, but the domain error must propagate.
      raised?.let { throw it }
    }
  }
```

At file bottom:

```kotlin
@PublishedApi
internal class KeyedJob(
  val job: Job,
  val done: Job,
)
```

Change the `jobs` declaration to `MutableMap<Any, KeyedJob>`. Update the
imports: add `kotlinx.coroutines.NonCancellable`, and remove
`kotlinx.coroutines.cancelAndJoin`. `withContext` is already imported.

**Verify**:
- `./gradlew :anchor:desktopTest` is all green, with the existing tests unmodified.
- `./gradlew :anchor:iosSimulatorArm64Test` is all green.

**B3. KDoc.** Keep `AnchorRuntime.kt`'s "Thread-safe"/"Memory-safe" paragraphs, but make them accurate:
- the lock is never held across a join;
- an unrelated key is never delayed;
- leftovers are purged on the next call.

In `Anchor.kt`'s `CancellableAnchor.cancellable` KDoc, add:
- "The new block starts only after the previous block for the same key has completed."
- "Cancellation is cooperative; wrap blocking calls in `runInterruptible`."

**Verify**: `./gradlew :anchor:compileKotlinIosSimulatorArm64` → exit 0.

**B4. Full gate.**

**Verify**: `./gradlew build` → exit 0.

## Test plan

Test-first, in B1. These tests use real threads and `Thread.sleep`, so they are JVM-only and live in `desktopTest`. They are deliberate real-parallelism exceptions to plan 014.

Create `anchor/src/desktopTest/kotlin/dev/kioba/anchor/CancellableKeyIsolationTest.kt`:

```kotlin
package dev.kioba.anchor

import dev.kioba.anchor.internal.AnchorRuntime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancellableKeyIsolationTest {
  private fun runtime(): AnchorRuntime<EmptyEffect, TestState, Nothing> =
    AnchorRuntime(initialState = { TestState(value = 0) }, effectScope = { EmptyEffect })

  @Test
  fun `at most one block per key runs concurrently`() =
    runBlocking {
      val anchor = runtime()
      val running = AtomicInteger(0)
      val maxSeen = AtomicInteger(0)
      (1..200).map {
        async(Dispatchers.Default) {
          anchor.cancellable("search") {
            val now = running.incrementAndGet()
            maxSeen.updateAndGet { m -> maxOf(m, now) }
            try {
              delay(2)
            } finally {
              running.decrementAndGet()
            }
          }
        }
      }.awaitAll()
      assertEquals(1, maxSeen.get(), "more than one cancellable block ran concurrently")
      assertEquals(0, anchor.jobs.size)
    }

  @Test
  fun `unique keys do not accumulate`() =
    runBlocking {
      val anchor = runtime()
      (1..1000).map { i -> async(Dispatchers.Default) { anchor.cancellable("q$i") { delay(1) } } }.awaitAll()
      assertEquals(0, anchor.jobs.size)
    }

  @Test
  fun `job cancelled from outside during mutex contention leaves no entry`() =
    runBlocking {
      val anchor = runtime()
      val k2Started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("k2") {
            try {
              k2Started.complete(Unit)
              awaitCancellation()
            } finally {
              withContext(NonCancellable) { delay(300) }
            }
          }
        }
      k2Started.await()
      val k1Started = CompletableDeferred<Unit>()
      val k1Caller =
        launch(Dispatchers.Default) {
          anchor.cancellable("k1") {
            k1Started.complete(Unit)
            awaitCancellation()
          }
        }
      k1Started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("k2") { } }
      delay(50)
      k1Caller.cancelAndJoin() // e.g. an outer cancellable / screen teardown cancels the caller
      second.join()
      first.join()
      assertTrue(anchor.jobs.isEmpty(), "stale entries left in jobs map: ${anchor.jobs.keys}")
    }

  @Test
  fun `slow cancellation cleanup on one key does not delay an unrelated key`() =
    runBlocking {
      val anchor = runtime()
      val started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("slow") {
            try {
              started.complete(Unit)
              awaitCancellation()
            } finally {
              withContext(NonCancellable) { delay(400) }
            }
          }
        }
      started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("slow") { } }
      delay(50)
      val t0 = System.nanoTime()
      val entered = CompletableDeferred<Long>()
      launch(Dispatchers.Default) {
        anchor.cancellable("other") { entered.complete((System.nanoTime() - t0) / 1_000_000) }
      }
      val waitedMs = entered.await()
      second.join()
      first.join()
      assertTrue(waitedMs < 100, "unrelated key blocked for ${waitedMs}ms")
    }

  @Test
  fun `blocking effect on one key does not stall an unrelated key`() =
    runBlocking {
      val anchor = runtime()
      val started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("search") {
            effect {
              started.complete(Unit)
              Thread.sleep(400) // blocking client call: not interruptible by cancellation
            }
          }
        }
      started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("search") { } }
      delay(50)
      val t0 = System.nanoTime()
      val entered = CompletableDeferred<Long>()
      launch(Dispatchers.Default) {
        anchor.cancellable("refresh") { entered.complete((System.nanoTime() - t0) / 1_000_000) }
      }
      val waitedMs = entered.await()
      second.join()
      first.join()
      assertTrue(waitedMs < 100, "unrelated key blocked for ${waitedMs}ms")
    }

  @Test
  fun `cancelling a caller queued behind a slow predecessor is prompt`() =
    runBlocking {
      val anchor = runtime()
      val started = CompletableDeferred<Unit>()
      val first =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            effect {
              started.complete(Unit)
              Thread.sleep(400)
            }
          }
        }
      started.await()
      val second = launch(Dispatchers.Default) { anchor.cancellable("k") { } }
      delay(50)
      val t0 = System.nanoTime()
      second.cancelAndJoin()
      val ms = (System.nanoTime() - t0) / 1_000_000
      first.join()
      assertTrue(ms < 100, "cancelling a queued caller took ${ms}ms")
    }

  @Test
  fun `cancelled queued caller does not let a third call overlap the predecessor`() =
    runBlocking {
      val anchor = runtime()
      val running = AtomicInteger(0)
      val maxSeen = AtomicInteger(0)

      fun enter() {
        val now = running.incrementAndGet()
        maxSeen.updateAndGet { m -> maxOf(m, now) }
      }
      val started = CompletableDeferred<Unit>()
      val a =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            effect {
              enter()
              started.complete(Unit)
              try {
                Thread.sleep(400) // blocking: ignores coroutine cancellation
              } finally {
                running.decrementAndGet()
              }
            }
          }
        }
      started.await()
      val b =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            enter()
            running.decrementAndGet()
          }
        }
      delay(50)
      b.cancelAndJoin()
      val c =
        launch(Dispatchers.Default) {
          anchor.cancellable("k") {
            enter()
            try {
              delay(20)
            } finally {
              running.decrementAndGet()
            }
          }
        }
      c.join()
      a.join()
      assertEquals(1, maxSeen.get(), "two blocks for key k ran at the same time")
    }
}
```

Expected results on `0bc430c`: 4 FAIL (stale entry, the 2 stall tests, overlap) and 3 PASS. With C2, 7 PASS.

These are the verification-run tests, renamed. Two were reformatted to multi-line lambdas, with no
logic change. With C2, the stale-entry test passes without any purge trigger. The cancelled `k1`
job's `NonCancellable` cleanup gets the uncontended lock, and there is no predecessor, so it removes
itself.

## Done criteria

Phase A:
- [ ] "Investigation results" appended: A1 failures, A2 green counts (JVM + iOS), A3 20/20 stress, A4 C1 comparison, and the recommendation
- [ ] No in-repo change other than this file (`git status`)

Phase B (post-GO):
- [ ] `CancellableKeyIsolationTest` observed failing (4) before B2 and all 7 passing after
- [ ] All existing `:anchor` tests pass unmodified on `desktopTest` and `iosSimulatorArm64Test`
- [ ] KDoc updated in `AnchorRuntime.kt` and `Anchor.kt`
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

- End of Phase A is a mandatory stop.
- A1: fewer than 4 regressions fail on unmodified code (drift).
- Any test hangs past 60 s. That indicates a deadlock, so report the thread dump (`jstack`) and do not "fix" it with timeouts.
- An existing test needs modification to pass.
- iOS results differ from JVM results.
- The A3 stress shows any flake in the overlap or at-most-one tests. That would mean the design does not hold, so report the failing interleaving.

## Maintenance notes

- Reviewer focus:
  - nothing suspends inside the swap's `withLock`;
  - `done` completes only after `previous.done`;
  - the `finally` removal guard `predecessorDone`;
  - the `RaisedException` path is unchanged (`ExecuteBoundaryTest`, `RaiseTest`).
- Kotlin/Native: no new fire-and-forget coroutine is introduced. `done` is a plain `Job()` completed from `invokeOnCompletion` handlers that cannot throw, so the SupervisorJob-without-handler abort (commit `687d4e5`) does not apply.
- Plan 014 (virtual-time cancellable tests) should list `CancellableKeyIsolationTest` as intentionally real-time.
- Plan 024 also edits `AnchorRuntime.kt`, but only the signals region. The second to land rebases trivially.
- Tracker: this resolves the residual substance of #129/#130. See spec 025 §7 for the #145 umbrella table.
