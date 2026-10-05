# Spec: Errors that end work silently: foreign cancellations, unhandled `raise`, a throwing `defect` handler, and `init` taking every listener down

- **Issue**: none yet. Plan 003's characterization tests found A1–A3 on 2026-09-29 (`SubscriptionIsolationTest`, branch `fix/003-subscription-restart`, commit `e8f6126`) and listed them as follow-ups in plan 003's "Investigation results". A4 turned up while verifying them. Recommend the maintainer open one issue.
- **Verdict**: CONFIRMED, all four defects:
  - **A1**: a `CancellationException` that is not the coroutine's own cancellation, such as a `withTimeout` expiry, never reaches `defect`.
  - **A2**: a `raise` with no `onDomainError` never reaches `defect`.
  - **A3**: a `defect` handler that throws inside `.anchor {}` is invoked twice.
  - **A4**: when `init` ends with a cancellation that `safeExecute` rethrows, every `connect` listener is cancelled.

  All four reproduce on desktop JVM and on the iOS simulator. They reproduce at the stack tip `0048d11`, at `e8f6126`, and on origin/master `0bc430c`, so they **predate the in-flight PRs**.
- **Severity**: P1. These are silent contract violations in documented usage.
  - `docs/errors.md:130` promises that `defect` runs "for any uncaught `Throwable` from your action".
  - A `withTimeout` around a network call is ordinary code, and when it expires, nothing reports it.
  - The damage is permanent: a subscription listener ends for the anchor's lifetime, and under A4 every listener ends.
  - Workarounds exist: `withTimeoutOrNull` or catching the timeout, always configuring `onDomainError`, and never throwing from `defect`. That keeps it P1, not P0.
- **Verified at**: `origin/fix/004-init-event-delivery` = `0048d11` (PR #273's tip, which includes #272), then `e8f6126` (plan 003's tests on top), then origin/master `0bc430c`. Done 2026-09-29, with probes in a disposable worktree that has since been removed.
- **Plan**: [`plans/030-silent-subscription-endings.md`](../030-silent-subscription-endings.md). It is Investigate-then-Act, because A1's scope and A2's routing need a maintainer decision (§6).
- **Related**:
  - Plan 003 is the base. Its `anchorErrors()` operator reuses the same routing, and three of its pinned tests flip here.
  - Plan 004 (#273) changed the `ContainerViewModel.init` ordering that A4 touches.
  - Plan 026 (#278) owns `cancellable`; see §3.5.
  - Plan 028's W12 rewrites `errors.md:41`.
  - Plan 006 covers anchor-test fidelity.

---

## 1. Problem statement and evidence

### 1.1 Mechanism

All error routing goes through `safeExecute`, and `ErrorHandling.kt` is identical on master. Condensed from `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt:9-47`:

```kotlin
// catchDomainError
catch (e: RaisedException) { onDomainError?.invoke(anchor, e.error as Err) ?: throw e }
// catchDefects
catch (e: Throwable) { if (!e.isNonFatal()) throw e; defect?.invoke(anchor, e) ?: throw e }
// safeExecute
catchDefects(anchor, defect) { catchDomainError(anchor, onDomainError) { block() } }
```

It is used at three boundaries:

- **Actions**: `ContainerViewModel.execute` is `viewModelScope.launch(Dispatchers.Default) { safeExecute { block } }` (`viewmodel/ContainerViewModel.kt:44-53`).
- **Subscription setup and `init`**: `ContainerViewModel.init` (`:55-69`).
- **Subscriptions**: once per event inside `.anchor {}` (`SubscriptionDsl.kt:46-53`), and once per listener in the chain-level `catch` that `handlers()` wraps around every `connect` chain (`internal/AnchorRuntime.kt:133-148`).

`isNonFatal()` is false for every `CancellationException`. The iOS actual is `this !is CancellationException` (`iosMain/.../internal/NonFatal.kt:6-7`), and the JVM and Android actuals list `CancellationException` among the fatal types. `RaisedException` extends `CancellationException` (`AnchorExceptions.kt:16-19`). Four defects follow from that.

- **A1: a foreign cancellation is never routed.** `safeExecute` rethrows a `TimeoutCancellationException` from `withTimeout`, and the `CancellationException` thrown by awaiting a `Deferred` cancelled elsewhere, past `defect`. When such an exception leaves a coroutine, kotlinx treats it as cancellation and reports nothing. In a subscription, the chain-level `catch` receives it and hands it to `safeExecute` again, which rethrows again, and the listener ends.
- **A2: an unhandled `raise` is never routed.** With no `onDomainError`, `catchDomainError` rethrows the `RaisedException`. `catchDefects` then rethrows it as fatal, so `defect` is never consulted.
- **A3: a throwing `defect` runs twice.** `.anchor {}` routes each event's error through `safeExecute`. If the `defect` handler itself throws, that exception leaves `.anchor {}` and reaches the chain-level `catch` (`AnchorRuntime.kt:142-147`), which routes it to `defect` a second time. The second throw ends the listener, and the containment handler (`AnchorRuntime.kt:172`) discards it.
- **A4: `init` cancels every listener.**
  - On the stack (#273), `ContainerViewModel.init` runs `subscribe()` and then `consumeInitial()` inside one `viewModelScope.launch` (`ContainerViewModel.kt:55-68`). `subscribe()` parents its `SupervisorJob` to that launch's Job (`AnchorRuntime.kt:165`).
  - When `init` ends with a `CancellationException` that `safeExecute` rethrows (A1 or A2), the launch body completes as cancelled and cancels its children, which include every listener.
  - On master, `consumeInitial()` runs before `subscribe()` inside one `safeExecute` (master `ContainerViewModel.kt:44-51`), so `subscribe()` is never reached. The mechanism differs but the outcome is the same.

### 1.2 Evidence (probes)

The probe class `SilentEndingProbeTest` has 15 tests, condensed in Appendix A. Each `A*` test asserts the behaviour this spec asks for, so a failure confirms the defect. Results were identical on desktop and on the iOS simulator. `defect` is configured and records everything unless the Setup column says otherwise.

| Probe | Setup | Actual today |
|---|---|---|
| A1 sub, timeout | `.anchor { withTimeout(1) { awaitCancellation() } }` on `Load(fail=true)`, then `Load(fail=false)` | `(loads, defects)` = `([], [])`; expected `([false], [TimeoutCancellationException])`. The good event is never handled. |
| A1 sub, foreign await | `.anchor { cancelledElsewhere.await() }` | `([], 0)`; expected `([false], 1)` |
| A1 execute | `ContainerViewModel.execute { withTimeout(1) { awaitCancellation() } }` | defects `[]` |
| A1 init | `init = { withTimeout(1) { awaitCancellation() } }` and `connect<Ping>` that sets state | `(defects, Ping handled, attached handlers)` = `(0, 0, 0)`; expected `(1, 1, 1)` |
| A2 sub | only `defect`; `.anchor { raise(NotFound) }`, then a good event | `([], [])`; expected `([false], [NotFound])` |
| A2 execute | only `defect`; `execute { raise(NotFound) }` | defects `[]` |
| A2 init | only `defect`; `init = { raise(NotFound) }` | `(0, 0, 0)` |
| A3 sub | `defect` records then throws; `.anchor { throw LoadFailure }` | `defect` saw `[action failed, defect handler failed]`; expected it once |
| A4 init | no handlers; `init = { raise(NotFound) }` | `(Ping handled, attached)` = `(0, 0)` |
| control | clean `init` | `(1, 1)`, passes |
| guard | clear the ViewModel while an action is suspended | no `defect` call, passes |
| guard | `onDomainError` throws, `defect` records | `defect` called once, listener survives, passes |
| characterization, execute mirror | `launch { safeExecute { … } }` with a recording `CoroutineExceptionHandler`: a timeout, then an unhandled raise | the job `isCancelled`, **nothing** reaches the exception handler, and `defect` is not called. Passes. |
| characterization, execute mirror | the same mirror, with a `defect` that throws | `defect` called once, and its exception escapes to the handler. Passes. |
| A1 cancellable | `cancellable("k") { withTimeout(1) { … } }` in the execute mirror | defects `[]`. Out of scope, see §3.5. |

Runs:

| Base | Target | Tests run | Failed |
|---|---|---|---|
| `0048d11` | desktop, full suite | 119 (107 existing + 12 probes) | 8, all `A*` probes |
| `0048d11` | iOS simulator, probe class | 12 | the same 8. No signal 6. |
| `e8f6126` | desktop, full suite | 133 (118 existing + 15 probes) | 10, all `A*` probes, including A4 and A1 cancellable |
| master `0bc430c` | desktop and iOS, probe class | 12 | the same 8 on each |

Plan 003 pins today's A1–A3 behaviour in `SubscriptionIsolationTest.kt` at `e8f6126`, at `:286` (raise with only `defect`), `:305` (timeout) and `:326` (throwing `defect`). Its in-flight branch comments each one "Pins current behavior that plans/030 is expected to change".

### 1.3 What the documented error model promises

- `docs/errors.md:5`: "Silent failures become impossible to write."
- `docs/errors.md:41`: "Omitting `onDomainError` means an unhandled `raise()` crashes the coroutine. Omitting `defect` lets unexpected exceptions propagate normally."
- `docs/errors.md:127-130`, the warning box: "The `defect` handler runs for `orDie()` calls **and** for any uncaught `Throwable` from your action."
- `docs/errors.md:162`: "Unexpected exception from a third-party library | caught automatically → `defect`".
- `Raise` KDoc (`Raise.kt:10-11`): "When used with [Anchor], `raise` propagates the error to the `onDomainError` handler configured via `create()`." It says nothing about a missing handler.
- `DefectAnchor` KDoc (`Anchor.kt:92-94`): "Provides the ability to escalate a domain error to a defect. A defect reaches the `defect` handler configured via `create()`."
- `create` KDoc (`RememberAnchorScope.kt:21-22`): "`onDomainError` An optional callback invoked when a domain error is raised. `defect` An optional callback invoked when an unexpected error occurs."
- `.anchor {}` KDoc (`SubscriptionDsl.kt:38-40`): "If the action calls [Raise.raise], the error is routed to the `onDomainError` handler without killing the subscription pipeline. If no handler is configured, the exception propagates and cancels the subscription." Plan 003's in-flight rewrite keeps the rule: "If the matching handler is not configured, the error ends the chain."

What the promises mean for each defect:

- **A1 contradicts the docs.** A `TimeoutCancellationException` is an uncaught `Throwable` from the action (`errors.md:130`), and nothing reports it: no handler, no log, no crash (`errors.md:5`).
- **A2 is half documented.**
  - The `.anchor {}` KDoc does say the listener ends.
  - That nothing reports it contradicts `errors.md:41` and `errors.md:5`. The docs say "crashes the coroutine", but the execute characterization shows it is cancelled silently.
  - No document states whether an unhandled domain error *should* reach `defect`. The closest statement is `errors.md:130` ("any uncaught `Throwable`"), and `orDie` already defines the escalation type, `DomainDefectException`.
- **The paths are consistent with each other.** All three boundaries share `safeExecute`, so A1 and A2 are silent on `execute`, `init` and subscriptions alike. Only the blast radius differs:
  - `execute` loses one action;
  - a subscription loses its listener for the anchor's lifetime;
  - `init` loses every listener (A4).
- **The test DSL diverges from production.** `runAnchorTest` always passes non-null recording handlers to `safeExecute` (`anchor-test/.../scopes/AnchorTestScope.kt:72-84`). Under test, an unhandled `raise` is recorded as if it had been handled as a domain error. In production nothing records it.
- **A3 differs between paths.**
  - On `execute`, a throwing `defect` runs once and its exception escapes (characterization).
  - In a subscription it runs twice.
  - That the listener then ends is consistent with the #239 contract: an unhandled failure ends only its own listener, as pinned by `an unhandled defect inside an anchor action ends only its listener`. **Only the second invocation is a defect.**

### 1.4 Why it matters

- It is silent on every platform: no `defect` call, no log and no crash.
- It is permanent. A dead listener is never restarted (plan 003 declined auto-restart), and A4 takes all of them.
- Ordinary code triggers A1: a `withTimeout` around a network call inside an action or a `.anchor {}`.

---

## 2. Goals and non-goals

**Goals**

- A `CancellationException` that is not the running coroutine's own cancellation reaches `defect` when it is configured, on every path. Inside `.anchor {}` the listener then survives, as it does for any other error that is handled per event.
- The coroutine's own cancellation is still never routed. That covers a cleared ViewModel, a cancelled subscribing scope, `flatMapLatest` switching to the next event, and `cancellable` superseding a job.
- An error handler runs at most once per failure. An exception thrown by a handler is never routed back into a handler.
- Nothing `init` does can cancel subscriptions.
- A2 follows the maintainer's decision (Q1). The recommendation is to escalate to `defect`.
- It stays native-safe: no new fire-and-forget coroutine without a `CoroutineExceptionHandler`.

**Non-goals**

- Restarting ended listeners (plan 003's decision: no).
- Making a failure loud when no handler is configured (Q2).
- `cancellable {}` swallowing a foreign cancellation (§3.5).
- anchor-test fidelity (plan 006).

---

## 3. Design

### 3.1 A1: classify a cancellation by the running coroutine

| Option | Sketch | Verdict |
|---|---|---|
| **(a) Active check** | In `catchDefects`, route a `CancellationException` other than `RaisedException` to `defect` when `currentCoroutineContext().isActive`, and rethrow it otherwise | **Recommended.** It handles `withTimeout`, foreign `await`s and closed channels alike. It is the distinction kotlinx itself recommends: after catching a `CancellationException`, check `isActive` or call `ensureActive()`. |
| (b) Timeouts only | Route only `TimeoutCancellationException` | Rejected: it misses a foreign `Deferred.await()` (probe "A1 sub, foreign await") |
| (c) Document only | Recommend `withTimeoutOrNull` | Rejected: `errors.md:130` would stay false |
| (d) Wrap before routing | Pass `defect` a non-cancellation wrapper | Rejected: it needs a new public type and hides the real type (Q4) |

Notes on (a):

- **Two existing tests flip, and they need rewriting, not deleting.**
  - `ExecuteBoundaryTest.kt:85`, `CancellationException in execute is never swallowed`.
  - `NonFatalTest.kt:25`, `CancellationException is fatal and rethrown even with defect handler`.

  Both throw `CancellationException("cancelled")` by hand inside an active `runBlocking`. Under (a) that cannot be told apart from a foreign cancellation, so it is routed. Their intent, that scope cancellation is never routed, survives if they are rewritten to cancel the coroutine for real. The prototype (§3.7) showed that these are the only two existing tests on the execute side that flip.
- **Hand-thrown cancellations change behaviour.** An action that throws `CancellationException()` by hand as a silent early exit now reaches `defect`. It should `return` instead. That is a narrow behaviour change for the CHANGELOG.
- With no `defect` configured, the exception is rethrown as today, silently (Q2).

### 3.2 A2: a domain error when `onDomainError` is absent

| Option | Sketch | Verdict |
|---|---|---|
| **(a) Escalate when `defect` exists** | In `safeExecute`: with `onDomainError`, call it; else with `defect`, `throw DomainDefectException(error)` inside `catchDefects`'s block, so it is routed exactly as `orDie(error)` would be; else rethrow as today | **Recommended** (Q1) |
| (b) Always escalate | Also with no handlers | Rejected. Today's silent stop would become an uncaught `DomainDefectException`, because `viewModelScope` has no `CoroutineExceptionHandler`. Android terminates the app and Kotlin/Native aborts the process, for every anchor that has a typed `Err` and no handlers. |
| (c) Pass the `RaisedException` itself | `defect(e)` | Rejected: it hands a `CancellationException` subtype to user code, which commonly rethrows those. It also differs from what `orDie` delivers. |
| (d) Document only | Fix `errors.md:41` | Rejected: `errors.md:130` would stay false, and listeners would keep dying silently |

Implementation note: throw the `DomainDefectException` *inside* `catchDefects`'s block. Wrapping `defect` as a stand-in `onDomainError` would run the handler inside `catchDefects`'s `try`, so a throwing `defect` would be routed a second time. That is A3 again, this time on the execute path.

Under (a), a subscription listener survives the error, because it is handled per event. That is already what the `.anchor {}` KDoc promises for handled errors.

### 3.3 A3: never route a handler's own exception again

| Option | Sketch | Verdict |
|---|---|---|
| **(a) Per-listener escape record** | `subscribe()` launches each listener with a private coroutine-context element. `.anchor {}`, and plan 003's `anchorErrors()`, record the exception that leaves their `safeExecute`. The chain-level `catch` rethrows that exact exception without routing it. | **Recommended.** It is exact, keeps exception types unchanged, and is confined to subscriptions. `execute` has a single boundary, so it cannot double-route. |
| (b) Marker wrapper | Wrap a handler's failure in an internal exception | Rejected. Every boundary would have to unwrap it: `ContainerViewModel` in 3 places, `AnchorTestScope` and `AnchorSequenceTestScope`. User operators placed after `.anchor {}` would still see the wrapper. |
| (c) `addSuppressed` marker | Tag the exception | Rejected: it pollutes stack traces, and it is a no-op for exceptions that have suppression disabled |
| (d) Swallow and survive | Drop a handler's failure and keep the listener | Rejected: it hides handler bugs |

Why it is safe to skip routing for *everything* that leaves `.anchor {}`: an exception that leaves its `safeExecute` is one of three things.

1. A handler's own failure. Routing it again is the bug.
2. An error with no matching handler. The chain-level `catch` would consult the same missing handler.
3. The coroutine's own cancellation, which is rethrown either way.

Skipping therefore differs from today only for case 1.

Limits:

- The record holds the last escaped exception per listener. If two `.anchor {}` steps in one listener fail concurrently, for example under `merge`, the earlier exception may be routed again. That is rare, and the code should document it.
- **Unverified risk.** kotlinx's JVM stack-trace recovery (on in debug mode, which `-ea` in tests enables) can substitute a *copy* of an exception that crosses a coroutine boundary, keeping the original as `cause`. A `.anchor {}` inside a `flatMapLatest` inner flow crosses one. The plan's test T10b exercises that case on desktop. If identity fails there, the plan says to compare against `cause` as well.

After the fix the listener still ends, because a handler that throws is an unhandled failure. Only the second invocation goes away.

### 3.4 A4: subscriptions outlive `init`

| Option | Sketch | Verdict |
|---|---|---|
| **(a) Re-parent** | In `ContainerViewModel.init`, call `subscribe()` on `CoroutineScope(currentCoroutineContext() + viewModelScope.coroutineContext.job)`. `subscribe()`'s `SupervisorJob` then becomes a child of the ViewModel instead of the init coroutine, and it keeps `Dispatchers.Default`, so #273's in-place start is unchanged. | **Recommended** (Q6) |
| (b) Separate launches | Launch `subscribe()` and `init` separately | Rejected: it loses #273's guarantee that handlers attach before `init` runs |
| (c) Catch every cancellation around `consumeInitial()` | | Rejected: it would also swallow real cancellation |

After A1 and A2, A4 is reached only when `init` raises with no handlers at all, or hits a foreign cancellation with no `defect`. The fix also covers iOS: PR #279's `AnchorContainer` wraps `ContainerViewModel` (`fix/005-ios-anchor-lifecycle`, `AnchorContainer.kt:33`).

### 3.5 Related but out of scope: `cancellable {}` swallows a foreign cancellation

`cancellable` runs its block in a child `launch` and captures only `RaisedException` (`AnchorRuntime.kt:251-257` and `:277`).

- A `withTimeout` that expires inside it completes the child as cancelled, `join()` returns normally, and nothing is reported.
- Probe "A1 cancellable" fails both before and after the §3.1 prototype.
- Plan 026's rewrite keeps the same capture (`fix/026-cancellable-key-isolation`, `AnchorRuntime.kt:206-208` and `:235`).
- The fix has the same shape as the existing capture: record a `CancellationException` caught while the child is still active, alongside `raised`, and rethrow it after `join()`.

See Q5.

### 3.6 Interactions

- **Plan 003 (`fix/003-subscription-restart`)**:
  - It is the base branch.
  - Its `anchorErrors()` is `catch { safeExecute { throw error } }`, so it inherits the A1 and A2 fixes automatically. It needs the A3 record.
  - Its three pinned tests flip.
  - Its tests `an inner flow that flatMapLatest cancels is not routed` and `cancelling the subscribing scope is not routed` guard design (a) and must stay green.
- **Plan 004 (#273)**: A4 edits the line #273 introduced. The `ContainerViewModel` KDoc ("once handled, it does not skip the other step") gets stronger.
- **Plan 026 (#278)**: see §3.5.
- **Plan 028**: W12 plans to write, for `errors.md:41`, "Without `onDomainError`, `raise()` stops the action silently". After 030 that is true only when *neither* handler is configured. Whichever plan lands second reconciles the text.
- **Plan 006**: the anchor-test divergence in §1.3 remains. A1 improves anchor-test, because a timeout inside `on {}` becomes a captured defect instead of an exception thrown out of `runAnchorTest`.
- **Inlining**: `safeExecute` and `catchDefects` are `public inline` and are inlined into anchor-test (`AnchorTestScope.kt:82`, `AnchorSequenceTestScope.kt:129`). The modules are released together, so the classification helper that `catchDefects` calls must be `@PublishedApi internal`.
- **Native**: nothing new is launched. The listeners switch from `flow.launchIn(supervised)` to `supervised.launch(element) { flow.collect() }`, in the same scope with the same containment handler.

### 3.7 Prototype (throwaway, reverted)

All four designs were applied together on `e8f6126` with the probes; the diff sketch is in the plan's Steps 3–6.

- Desktop ran 133 tests with 7 failures. The iOS simulator ran 129 tests with the same 7 failures, and there was no signal 6.
- The 7 failures:
  - the 3 plan-003 pinned tests, which are expected flips;
  - `ExecuteBoundaryTest` "`CancellationException in execute is never swallowed`" and `NonFatalTest` "`CancellationException is fatal and rethrown even with defect handler`", which are expected flips (§3.1);
  - the probe that characterizes today's silent execute behaviour, which is expected to change;
  - "A1 cancellable", which is out of scope (§3.5).
- Every other test passed, including all of `InitEventDeliveryTest`, `EventBusReentrancyTest`, `ExecuteBoundaryTest`'s other routing tests, and all of the `A*` probes.

---

## 4. Behaviour and API changes

- **API: non-breaking.**
  - No public signature changes.
  - New internal pieces: a coroutine-context element, and a `@PublishedApi internal` classification helper.
  - `DomainDefectException` is reused as is.
- **Behaviour**: these changes only add `defect` calls where `defect` is configured.
  - A1: `defect` receives foreign cancellations. An app whose `defect` shows an error UI now shows it when a request times out, where before nothing happened.
  - A2 (if Q1 is (a)): with `defect` but no `onDomainError`, `defect` receives `DomainDefectException(error)` where before nothing happened.
  - In both cases, a subscription listener **survives** instead of ending.
  - A3: `defect` is called once instead of twice. The listener still ends.
  - A4: listeners survive anything `init` does.
  - Breaking in one narrow case: a `CancellationException` thrown by hand while the coroutine is active now counts as a failure. Record it in the 0.1.9 CHANGELOG (plan 027).
- **KDoc and docs**: `errors.md:41` and `:127-131`, the `.anchor {}` / `connect` / `anchorErrors` KDoc, the `create` `@param`s, the `Raise` KDoc and the `ContainerViewModel` KDoc.

## 5. Acceptance criteria

1. The regression tests in the plan's Test plan (T1–T4, T6–T8, T10, T10b, T11, T14) fail on the base and pass after the fix. T10b and T11 are predictions and must be checked in Phase A.
2. The guards (T5, T9, T12, T13, T15) and plan 003's two cancellation tests pass both before and after.
3. The two hand-thrown-cancellation tests are rewritten to cancel for real and pass before and after. New unit tests for a foreign cancellation on an active coroutine fail before and pass after.
4. Plan 003's three pinned tests are flipped to assert the new behaviour.
5. `./gradlew :anchor:desktopTest :anchor:iosSimulatorArm64Test` passes with no signal 6. `:anchor-test:allTests` and `./gradlew build` exit 0.
6. The KDoc and `docs/errors.md` state the rules, and `docs/llms-full.txt` is regenerated.

## 6. Open questions for the maintainer

- **Q1 (A2 routing).** When `onDomainError` is absent, should `raise` escalate to `defect`? The options are (a) escalate as `DomainDefectException(error)` when `defect` is configured, (b) always escalate, or (d) change the docs only. **Recommended: (a).** It matches `errors.md:130` and `orDie`, it applies to all three paths at once, and it adds no crash.
- **Q2 (no handler at all).** A `raise` with neither handler, or a foreign cancellation with no `defect`, can stay silent and be documented, or it can be made loud. **Recommended: stay silent and document it.** Loud would mean Android terminating the app and Native aborting it, because `viewModelScope` has no handler. It would be a new crash for existing apps.
- **Q3 (A1 scope).** Route (a) any foreign cancellation, or (b) only `TimeoutCancellationException`? **Recommended: (a).** That accepts rewriting the two hand-thrown-cancellation tests and the narrow behaviour change in §4.
- **Q4 (what `defect` receives for A1).** The original exception or a wrapper? **Recommended: the original.** A `TimeoutCancellationException` says exactly what happened.
- **Q5 (`cancellable`, §3.5).** **Recommended:** make it a conditional last step of 030 once #278 has merged, using the same capture pattern as `raised`. If #278 is still open by then, defer it to a follow-up.
- **Q6 (A4 in 030).** **Recommended: yes.** It is a one-expression change in `ContainerViewModel.init`, and without it one unrouted `init` failure silently disables every listener.
- **Q7 (release).** Ship in 0.1.9? **Recommended: yes**, with the §4 behaviour changes in plan 027's CHANGELOG.
- **Q8 (cause).** Should the escalated `DomainDefectException` carry the `RaisedException` as its `cause`, for stack traces? That needs a second constructor on a public class (binary compatible if it is added as a secondary constructor). **Recommended: not now.**

## 7. Relationship to existing plans

| Plan | Relationship |
|---|---|
| 003 | Base branch. It pins A1–A3; 030 flips those pins. `anchorErrors()` needs the A3 record. |
| 004 | A4 re-parents the `subscribe()` call that #273 introduced. The two are compatible, and #273's in-place start is unchanged (prototype). |
| 026 | §3.5 `cancellable` gap (Q5). |
| 027 | The CHANGELOG lists the §4 behaviour changes. |
| 028 | W12's `errors.md:41` text follows whichever lands second. |
| 006 | The anchor-test divergence on unhandled `raise` remains; follow up there. |

## Appendix A: probe code (condensed; all run on `0048d11`, `e8f6126` and `0bc430c`)

The fixtures are `TestState`, `TestError.NotFound` and `EmptyEffect` from `commonTest`, plus:

```kotlin
private sealed interface ProbeEvent : Event {
  data class Load(val fail: Boolean) : ProbeEvent
  data object Ping : ProbeEvent
}
private class ProbeFailure(message: String) : RuntimeException(message)

// withListeners: launch { supervisor.complete(subscribe()) }; wait until
// _emitter.subscriptionCount >= n; run the block; cancelAndJoin in finally.
// This is plan 003's SubscriptionIsolationTest helper.
// inViewModel: ViewModelProvider.create(owner, ContainerViewModelFactory { ContainerViewModel(runtime) });
// run the block; store.clear() in finally. This is InitEventDeliveryTest's helper.

// A1 sub, timeout
createAnchor(defect = { e -> defects.update { it + e } }) {
  connect<ProbeEvent.Load> { events ->
    events.anchor { event ->
      if (event.fail) withTimeout(1) { awaitCancellation() }
      loads.update { it + event.fail }
    }
  }
}
// emit Load(true), Load(false); withTimeoutOrNull(1_000) { loads.first { it.isNotEmpty() } }
// assertEquals(listOf(false) to listOf("TimeoutCancellationException"),
//              loads.value to defects.value.map { it::class.simpleName })

// A1 init / A2 init / A4 init
createAnchor(init = { withTimeout(1) { awaitCancellation() } /* or raise(TestError.NotFound) */,
             defect = { e -> defects.update { it + e } } /* omitted for A4 */) {
  connect<ProbeEvent.Ping> { events -> events.anchor { reduce { copy(value = 1) } } }
}
// inViewModel { delay(300); emit Ping; withTimeoutOrNull(1_000) { viewState.first { it.value == 1 } }
//   assertEquals(Triple(1, 1, 1),
//     Triple(defects.size, viewState.value.value, anchor._emitter.subscriptionCount.value)) }

// A2 execute: only defect; vm.execute { (this as Anchor<EmptyEffect, TestState, TestError>).raise(TestError.NotFound) }

// A3 sub
createAnchor(defect = { e -> defects.update { it + e.message }; throw IllegalStateException("defect handler failed") }) {
  connect<ProbeEvent.Load> { events -> events.anchor { throw ProbeFailure("action failed") } }
}
// withListeners(1) { sup -> emit Load(true); sup.awaitLiveListeners(0)
//   assertEquals(listOf("action failed"), defects.value) }   // actual: [action failed, defect handler failed]

// Execute mirror (native-safe): a scope with SupervisorJob + Dispatchers.Default + a recording
// CoroutineExceptionHandler; job = scope.launch { safeExecute(anchor, anchor.onDomainError, anchor.defect) { anchor.block() } }
```
