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

## Investigation results (2026-09-29)

Base: `origin/fix/004-init-event-delivery` (`0048d11`, PR #273, stacked on
#272). Worktree `scratchpad/impl-003`, branch `fix/003-subscription-restart`,
local commit `e8f6126` (🧪 characterization tests only). **Not pushed, no PR.**

**Drift.** "Current state" no longer matches `subscribe()`. #239 launches each
handler on its own under a `SupervisorJob`, so there is no `merge()`.
`687d4e5` added the no-op containment `CoroutineExceptionHandler`. #273 added
`HandlerDispatcher`, which starts handlers in place. #272 added
`EVENT_BUFFER_CAPACITY`. `handlers()` still wraps every chain in
`catch { safeExecute { throw e } }`. Only that `catch` handles errors at the
chain level.

### Which error sites end a listener today

Pinned by `anchor/src/commonTest/.../SubscriptionIsolationTest.kt`: 11 tests,
all passing on desktop 118/118, Android host 114/114 and iOS simulator
114/114. Ten `--rerun`s on desktop were green.

| Error site | Handler configured | Today |
|---|---|---|
| `.anchor {}` action throws | `defect` | Routed per event. The listener survives. |
| `.anchor {}` action `raise`s | `onDomainError` | Routed per event. The listener survives. |
| Operator outside `.anchor {}` (e.g. `map` before it) | `defect` | Routed once, then the listener **ends**. The next event is never seen, and `Created` is not redelivered. |
| `flatMapLatest` inner flow throws | `defect` | Routed once, then the listener **ends**. This is the #182 gap. |
| `.anchor {}` action throws | none | That listener ends silently (contained). The sibling survives. |
| `.anchor {}` action `raise`s | only `defect` | Ends **silently**. `defect` is never called: `RaisedException` is a `CancellationException`, which `safeExecute` treats as fatal. |
| `withTimeout` expires inside `.anchor {}` | `defect` | Ends **silently**. `defect` is never called: `TimeoutCancellationException` is fatal to `safeExecute`. |
| The `defect` handler itself throws | `defect` | `defect` runs twice, first for the error, then for its own exception via the chain `catch`. Then the listener ends. |
| Subscribing scope cancelled | `defect` | Every listener stops. No handler is called. |
| User `catch` inside the inner flow | n/a | The listener survives. This is the existing workaround. |

Covered elsewhere:
- Upstream throw with no handler: only that listener ends (`SubscriptionDslTest`).
- `onStart { throw }`: routed once, and the listener ends before it
  subscribes (`InitEventDeliveryTest`).
- An error in the `subscriptions {}` body throws out of `subscribe()` before
  any handler launches. `ContainerViewModel` routes it (`ExecuteBoundaryTest`).

Plan Step 1 mapping:
- Test 1 (sibling survives) and test 5 (cancellation) pass.
- Test 2 (restart) fails as written. Its inverse is pinned.
- Test 4 (`Created` twice) fails as written. Its inverse is pinned.
- Test 3 (unhandled failure kills only its listener) now **passes**. The plan
  expected it to fail, but #239 fixed it, which is the drift the triage note
  anticipated.

### Restart-design probes

These probes are throwaway and not committed:
`scratchpad/RestartProbeTest.kt.txt`, `scratchpad/probe-b-retrywhen.patch`
and `scratchpad/probe-a-next-event.patch`. All ran on desktop in 1 s windows.
(b) is the plan's design: `retryWhen`, with 100 ms after the first retry.
(a) is a prototype that restarts on the next bus event and seeds the
restarted chain with that event instead of `Created`.

| Probe | Today | (b) | (a) |
|---|---|---|---|
| E1: refresh on `Created`, always offline, idle | 1 fetch, 1 defect | **11 fetches, 11 defects per second, unbounded** | 1, 1 |
| E2: `onStart { throw }`; idle / +5 other events / +5 matching events (defect count) | 1 / 1 / 1 | 11 / 11 / 12 | 1 / 2 / 3 (bursts collapse: events are lost in the resubscribe gap) |
| E3: plan test 2 (fail, ok, ok) | [] | [ok, ok] | [ok, ok] |
| E4: events seen by a restarted `connect<Event>` | Created, Load | Created, Load, **Created**, Other | Created, Load, Other |
| E5: error handler emits; a sibling answers with a matching event | 1 fetch | 1 | **7 475** |
| E7: same loop, error inside `.anchor {}` | **34 706** | n/a | n/a |
| Desktop suite | green | 4 failures: 3 pinning tests, plus #273's `a handler that ends before subscribing does not hold up init` (sees `defect` twice) | 3 failures: the 3 pinning tests |

Readings:
- **(b) fails the no-hot-loop bar.** A permanently failing listener hits
  `defect` and its effect about 10 times a second, forever. Every restart
  also redelivers `Created`, so a `Created`-triggered refresh turns into a
  10 Hz auto-retry.
- **(a) is bounded by input:** it does nothing while idle. E5 loops, but E7
  shows that today's per-event routing inside `.anchor {}` already allows the
  same app-level feedback loop. That loop comes from "survive" semantics in
  general, not from restart.
- **(a) is not unambiguous.** Three things are still open:
  1. Matching the event needs `A`, which only `connect<A>` knows. `connect`
     is `public inline` and calls the `@PublishedApi` `wrap`, so already
     compiled callers need `wrap` to keep its signature, meaning a new
     overload. A runtime-only variant restarts on *any* event.
  2. The waiting subscription ends before the restarted one starts, so events
     in between are dropped. A channel handoff would close that gap, but it
     stalls the bus for handlers that ignore their events.
  3. It has to be decided whether a restart delivers the triggering event,
     `Created`, or nothing. A `connect<Created>` listener would never restart.

**Decision: STOP after Phase A.** The plan's design does not bound restarts,
and the bounded alternative has open semantic choices.

### Options for the maintainer

- **(a) Restart on the next matching event**, seeded with that event, with no
  `Created`.
  - For: no idle loop. `Created` stays once per lifetime. After an outage,
    the user's next Refresh works on the first press.
  - Against: needs `connect`-level plumbing (a new `@PublishedApi`
    overload). It drops events during the resubscribe, or needs a channel
    handoff with its own stall hazard. `connect<Created>` listeners, and ones
    that ignore their events, wait for an event that may never come. It is a
    behavior change for every existing chain.
- **(b) Immediate restart with backoff** (the plan's design).
  - For: the simplest to build. Listeners that ignore their events (such as
    `repository.observe()`) recover without needing an event.
  - Against: it floods unless restarts are capped or backed off
    exponentially with a reset on success. It redelivers `Created`. It breaks
    #273's test. A cap only brings back today's dead listener, later.
- **(c) No restart; document it.**
  - For: zero behavior change, and it matches Kotlin Flow, where a failed
    flow completes. The inner-flow `catch` workaround is pinned by a test.
  - Against: the pitfall stays. From inside a chain, a user cannot route a
    caught error to `onDomainError` or `defect` without their own plumbing.
- **(d) Opt-in routing for inner flows.** Add a `SubscriptionsScope` operator
  (name to be decided, e.g. `Flow<T>.catchAnchor()`) implemented as
  `catch { e -> safeExecute(anchor, onDomainError, defect) { throw e } }`,
  used inside `flatMapLatest { … }`. Probe E6 on today's code: two failing
  Loads gave two `defect` calls, nothing ran while idle, and the next good
  Load was processed.
  - For: additive, with no change to default behavior. Bounded by
    construction, at one error per inner run. Same semantics as `.anchor {}`
    routing.
  - Against: a new public API that users must opt into. Errors outside inner
    flows still end the listener; that stays documented.

**Recommendation:** (d) together with (c)'s docs. The documented contract
stays "an error outside `.anchor {}` ends the listener"; the routing operator
covers inner flows, and the docs show the pattern. If automatic recovery is
wanted, prefer (a) with a minimum spacing between restarts over (b), and
design it as its own plan.

**Separate follow-ups found here** (out of this plan's scope):
1. A `withTimeout` inside an `.anchor {}` action, or any non-scope
   `CancellationException`, ends the listener silently and never reaches
   `defect`.
2. `raise` with only `defect` configured ends the listener silently.
3. A throwing `defect` handler is invoked again with its own exception.

## Execution results (2026-09-29)

**Maintainer decision.** Option (d), the opt-in operator, plus option (c),
the docs. There is no automatic restart; (a) and (b) are rejected.

**PR**: https://github.com/kioba/anchor/pull/280. Branch
`fix/003-subscription-restart` @ `50c38b3`, base
`fix/004-init-event-delivery`. It is stacked on #273, which is stacked on
#272. The worktree has been removed.

**Commits**:
- `e8f6126` 🧪 Characterization tests: `SubscriptionIsolationTest`, 11 tests.
- `c07ff71` ✨ `anchorErrors()` plus `AnchorErrorsTest` (6 tests), and KDoc on
  `connect` and `.anchor {}`.
- `9ab5f36` 🧪 Marks three characterization tests as "plans/030 is expected
  to change":
  - a `raise` with only `defect`;
  - a `withTimeout` inside `.anchor {}`;
  - a throwing `defect` handler that runs twice.
- `50c38b3` 📝 `docs/concepts.md` gains "Errors in subscriptions", and
  `docs/llms-full.txt` is regenerated.

**The operator.**
- `public fun <T> Flow<T>.anchorErrors(): Flow<T>` is a member of
  `SubscriptionsScope`, implemented as
  `catch { e -> safeExecute(anchor, onDomainError, defect) { throw e } }`.
- It routes the same way `.anchor {}` does, then completes only that flow.
  Each failing inner run is routed once and never retried.
- The collector's own cancellation, including `flatMapLatest` switching, is
  never routed.
- With no matching handler, the error is rethrown and ends the chain, as
  before.
- `AnchorRuntime.kt` is untouched.
- Name: it mirrors `.anchor {}` (values to the anchor, failures to the
  anchor), and avoids `recover` (already Anchor's API), `retry*` (the
  rejected semantics) and shadowing `catch`.

**Tests.** Against a no-op stub, the 3 routing tests failed: each timed out
because the listener had ended. The 3 guard tests passed: flatMapLatest
cancellation not routed, scope cancellation not routed, and an unhandled
error ending only its listener. With the operator, all 6 pass.

**Gate.** Full `./gradlew build`: BUILD SUCCESSFUL in 4m 37s.
- `:anchor`: desktop 124, Android host 120, iOS simulator 120.
- `:anchor-test`: 73, 73 and 73.
- `features:config`: 6 and 6. `features:main`: 6 and 6.
- `features:counter`: 4 (iOS).
- `umbrella`: 1 (Android host) and 2 (iOS).
- All green. `generate-llms-full.sh` leaves the tree clean.

**Deviations.**
1. Step 2, the `retryWhen` restart, is not implemented; the maintainer chose
   option (d).
2. The test file is `AnchorErrorsTest.kt` alongside
   `SubscriptionIsolationTest.kt`. The operator's tests are separate from the
   characterization tests.
3. `plans/README.md` is not updated; the coordinator maintains the index.

**Follow-ups.**
- Plan 030 covers the silent endings and the handler that runs twice. If it
  starts routing foreign `CancellationException`s, re-check the `anchorErrors`
  KDoc. Its "never routed" claim covers only the collector's own
  cancellation, so it stays true.
- The docs site has no `SubscriptionsScope` entry in `docs/api.md`; plan 028
  may add `connect` and `anchorErrors` there.
