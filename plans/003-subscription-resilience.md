# Plan 003: Subscription resilience — isolate sibling listeners and restart handled failures (#182)

> **Triage note (2026-09-29, origin/master `0bc430c`)**: PR #239 merged 2026-06-25, and commit 687d4e5 added a no-op `CoroutineExceptionHandler` to the supervised subscription scope. A subscription exception that escapes `safeExecute` is now silently discarded on every platform (`AnchorRuntime.kt:114-121`); it no longer crashes Android. Re-check this plan's premise and re-scope it to the restart-on-handled-error gap before executing.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt anchor/src/commonTest/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `492f7bc`, 2026-06-11
- **Issue**: https://github.com/kioba/anchor/issues/182 (refined by this plan)

## Why this matters

Subscriptions (`listen { ... }` chains) are long-lived reactive listeners.
Issue #182 says "a single subscription throwing collapses every sibling". The
code has partially moved since: a per-flow `catch` now routes errors to the
configured handlers, so *handled* errors no longer kill siblings. But two real
problems remain, confirmed by reading the current code:

1. **A handled error still permanently kills its own listener.** `catch`
   terminates the upstream flow; nothing restarts it. One transient network
   error inside a `flatMapLatest { effect.fetchData() }` chain silently
   disables that listener for the rest of the screen's life.
2. **An unhandled error (no `onDomainError`/`defect` configured, or a handler
   that itself throws) escapes the merged flow**, killing ALL listeners at once
   and surfacing as an unhandled coroutine exception (process crash on Android).

The fix: restart a listener after a handled error, and isolate listeners from
each other so an unhandled failure kills only itself.

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:84-113`:

  ```kotlin
  private val emitter: SharedFlow<Event> =
    _emitter
      .asSharedFlow()
      .onSubscription { emit(Created) }
  ...
  private suspend fun <T : Event> SharedFlow<T>.handlers(): Flow<Any?> =
    SubscriptionsScope<R, S, Err>(
      chain = this,
      anchor = this@AnchorRuntime,
      effect = effect,
      onDomainError = onDomainError,
      defect = defect,
    ).also { scope -> subscriptions?.invoke(scope) }
      .flows
      .map { flow ->
        flow.catch { e ->
          safeExecute(this@AnchorRuntime, onDomainError, defect) {
            throw e
          }
        }
      }.merge()

  suspend fun CoroutineScope.subscribe(): Job =
    emitter
      .handlers()
      .launchIn(this)
  ```

  Note: `onSubscription { emit(Created) }` emits into **that subscriber's
  downstream only** (the block receiver is the `FlowCollector`), so each
  listener flow receives its own `Created` on (re)subscription.
- `safeExecute` (`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt:36-47`)
  routes `RaisedException` → `onDomainError`, non-fatal `Throwable` → `defect`,
  and **rethrows** when the matching handler is null. `CancellationException`
  is fatal (`isNonFatal() == false`) and always rethrown — preserve that.
- Errors thrown inside a `.anchor { action }` step are *already* caught per-element
  by `safeExecute` in `SubscriptionDsl.kt:46-53` without terminating the flow.
  The `catch` in `handlers()` only sees errors from user flow operators
  (e.g. an upstream `effect.fetchData()` flow failing) or unhandled rethrows.
- `ContainerViewModel.init` (`anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt:44-53`)
  launches `consumeInitial()` then `subscribe()` inside one
  `viewModelScope.launch(Dispatchers.Default)`.
- Existing tests covering nearby behavior:
  `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionDslTest.kt` and
  `RaiseTest.kt` — use them as structural patterns (manual `AnchorRuntime`
  construction, `runBlocking`/coroutine orchestration, kotlin.test).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Core module tests (fast) | `./gradlew :anchor:desktopTest` | exit 0 |
| All targets for module | `./gradlew :anchor:build` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt`
  (only `handlers()` / `subscribe()` and a possible private helper)
- `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionIsolationTest.kt` (create)

**Out of scope**:
- `SubscriptionDsl.kt` (its per-action `safeExecute` already behaves correctly).
- `ErrorHandling.kt` / `NonFatal.kt` semantics.
- `ContainerViewModel.kt` init **ordering** (that is plan 004; coordinate via
  dependency order — this plan lands first).
- Any change to when `Created` is emitted for the *initial* subscription.

## Git workflow

- Branch: `fix/subscription-resilience`
- Commit style: gitmoji, e.g. `🐛 Isolate subscription listeners and restart after handled errors`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Characterization tests (write first, watch them fail/pass as noted)

Create `SubscriptionIsolationTest.kt` in `anchor/src/commonTest/`. Build
runtimes directly via `AnchorRuntime(...)` with a `subscriptions` block that
registers **two** `listen` chains (pattern: `SubscriptionDslTest.kt`). Drive
them by calling `runtime.emit { ... }` after `subscribe()`-ing in a test scope.

1. `handled failure in one listener does not affect sibling` — listener A's
   flow throws a plain `RuntimeException` on a trigger event; a `defect`
   handler is configured. Assert listener B still processes subsequent events.
   (Expected to PASS today — characterization.)
2. `listener restarts after handled failure` — after listener A's handled
   throw, emit another A-targeted event; assert A processes it.
   (Expected to FAIL today — `catch` completed A permanently.)
3. `unhandled failure kills only the failing listener` — NO handlers
   configured; listener A throws; assert B keeps processing.
   (Expected to FAIL today — everything collapses.)
4. `restarted listener re-receives Created` — listener A reacts to `Created`;
   after a handled failure, assert A received `Created` twice.
   (Documents restart semantics; expected to FAIL today.)
5. `cancellation propagates` — cancel the subscribing scope; assert both
   listeners stop and no defect handler was invoked with a
   `CancellationException`.

**Verify**: `./gradlew :anchor:desktopTest` → tests 2, 3, 4 fail; 1, 5 pass.
If the failure pattern differs, STOP and report.

### Step 2: Restart-on-handled-error + per-listener isolation

Restructure `AnchorRuntime` (keep names/visibility; signature of
`subscribe()` unchanged — `ContainerViewModel` and `iosMain` call it):

```kotlin
private suspend fun <T : Event> SharedFlow<T>.handlerFlows(): List<Flow<Any?>> =
  SubscriptionsScope<R, S, Err>(
    chain = this,
    anchor = this@AnchorRuntime,
    effect = effect,
    onDomainError = onDomainError,
    defect = defect,
  ).also { scope -> subscriptions?.invoke(scope) }
    .flows
    .map { flow ->
      flow.retryWhen { e, _ ->
        var handled = true
        try {
          safeExecute(this@AnchorRuntime, onDomainError, defect) { throw e }
        } catch (rethrown: Throwable) {
          handled = false
          if (rethrown is CancellationException) throw rethrown
        }
        handled
      }
    }

suspend fun CoroutineScope.subscribe(): Job {
  val flows = emitter.handlerFlows()
  return launch {
    supervisorScope {
      flows.forEach { flow ->
        launch { flow.collect {} }
      }
    }
  }
}
```

Behavior encoded here (mirror it in KDoc on `subscribe`):
- handled error → `retryWhen` resubscribes that listener (it re-receives
  `Created` via `onSubscription` — per-listener re-init, test 4);
- unhandled error → rethrown out of `retryWhen`, failing only that listener's
  `launch`; `supervisorScope` keeps siblings alive (test 3); the exception
  still reaches the scope's `CoroutineExceptionHandler` — unchanged fail-loud
  contract;
- `CancellationException` always propagates (test 5).

Add a retry guard against tight failure loops: if the flow fails immediately
and repeatedly, `retryWhen`'s `attempt` parameter is available — cap is NOT
needed for correctness, but add `if (attempt > 0) delay(RETRY_BACKOFF_MS)` with
`private const val RETRY_BACKOFF_MS = 100L` to avoid a hot spin when a listener
fails instantly on resubscribe.

Required imports: `kotlinx.coroutines.flow.retryWhen`,
`kotlinx.coroutines.supervisorScope`, `kotlinx.coroutines.delay`,
`kotlin.coroutines.cancellation.CancellationException`; remove now-unused
`catch`, `launchIn`, `merge` imports if nothing else uses them.

**Verify**: `./gradlew :anchor:desktopTest` → all SubscriptionIsolationTest
tests pass, and the existing suite (`SubscriptionDslTest`, `RaiseTest`,
`StandaloneRecoverTest`, `NonFatalTest`, `ExecuteBoundaryTest`, cancellable
tests) stays green.

### Step 3: All-targets build

**Verify**: `./gradlew :anchor:build` → exit 0 (compiles for iOS/Android too).
Then `./gradlew build` → exit 0.

## Test plan

Step 1 is the test plan (5 cases, written before the fix). Structural pattern:
`SubscriptionDslTest.kt`. Verification: `./gradlew :anchor:desktopTest` all
green after Step 2.

## Done criteria

- [ ] All 5 `SubscriptionIsolationTest` cases pass
- [ ] Existing `:anchor` tests unchanged and green
- [ ] `merge()` no longer used in `AnchorRuntime.kt`
      (`grep -n "merge()" anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt` → no matches)
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- Step-1 expected pass/fail pattern doesn't match (the runtime has drifted or
  the analysis is wrong — report actual behavior per test).
- `retryWhen` + `onSubscription`-Created interaction produces duplicate
  processing in *non-restart* paths (test 1 or existing tests start failing in
  a way traceable to multiple `Created` deliveries on first subscribe).
- Keeping `subscribe()`'s signature requires changes in `iosMain` or
  `ContainerViewModel` beyond imports — those files are out of scope here.

## Maintenance notes

- Plan 004 changes *when* `subscribe()` is awaited relative to `init` — it
  builds on the `handlerFlows()` list introduced here (it needs the flow count
  to await attachment). Land this plan first.
- The restart re-delivers `Created` to the restarted listener. For a listener
  that triggers a refresh on `Created`, a failure now causes an automatic
  retry-with-refresh — document this in the docs site when plan 012/013-era
  docs are touched (deferred here).
- Reviewer: scrutinize that an unhandled listener failure still surfaces
  loudly (supervisorScope reports to the uncaught handler) — silently eating
  it would hide real bugs.
