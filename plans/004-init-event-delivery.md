# Plan 004: Deliver events emitted during init — subscribe before consumeInitial

> **Triage note (2026-09-29, origin/master `0bc430c`)**: NEW DEFECT in scope, with no GitHub issue yet (probe-confirmed by the #266 verifier): `ContainerViewModel.kt:45-51` runs `consumeInitial()` and `subscribe()` in one `safeExecute` block, so if `init` raises a domain error, `subscribe()` never runs and the screen has no subscriptions. Fix it together with this plan's subscribe-before-init restructuring. Plan 023 (the event-bus deadlock) also touches event delivery; order 023 first.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt`
> Plan 003 intentionally changes `AnchorRuntime.subscribe()` — that diff is
> expected. Read the live `subscribe()` implementation before starting; if plan
> 003 has NOT landed (no `supervisorScope` in `AnchorRuntime.kt`), STOP — this
> plan's step 2 assumes `handlerFlows()` exists.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: MED
- **Depends on**: plans/003-subscription-resilience.md
- **Category**: bug
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

`emit { SomeEvent }` inside an anchor's `init` block is legal API that silently
does nothing: the internal event flow is a bare `MutableSharedFlow()` (replay 0,
buffer 0 — emissions with no subscribers are dropped by SharedFlow contract),
and `ContainerViewModel` runs `consumeInitial()` *before* `subscribe()` attaches
the listeners. Any event-driven setup flow (e.g. `init { emit { Refresh } }`)
is lost nondeterministically. Fix: attach subscriptions first, await
attachment, then run init.

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:61-63`:

  ```kotlin
  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _emitter: MutableSharedFlow<Event> = MutableSharedFlow()
  ```

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt:44-53`:

  ```kotlin
  init {
    viewModelScope.launch(Dispatchers.Default) {
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
        with(anchor) {
          subscribe()
        }
      }
    }
  }
  ```

- `consumeInitial()` (`AnchorRuntime.kt:89-91`) invokes the user `init` block.
- After plan 003, `subscribe()` has the shape:

  ```kotlin
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

- Each listener flow collects `emitter` (a wrapper around `_emitter`), so once
  all listeners are attached, `_emitter.subscriptionCount` ≥ number of flows.
- The same init-before-subscribe ordering also exists on iOS via
  `ContainerViewModel` (shared code) — fixing `ContainerViewModel` fixes both.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Core tests | `./gradlew :anchor:desktopTest` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt`
  (subscribe-attachment await only)
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt`
  (init ordering only)
- `anchor/src/commonTest/kotlin/dev/kioba/anchor/InitEventDeliveryTest.kt` (create)

**Out of scope**:
- Adding replay/buffer to `_emitter` — replay would re-deliver old events to
  every listener restart introduced by plan 003; do not do it.
- The signal (`_signals`) pre-subscription drop — UI-side concern, documented
  in plan 002.
- Closing the race for user-triggered `execute()` calls that emit before init
  completes — see Maintenance notes.

## Git workflow

- Branch: `fix/init-event-delivery`
- Commit style: gitmoji, e.g. `🐛 Attach subscriptions before running init`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Failing test first

`InitEventDeliveryTest.kt` (pattern: `SubscriptionIsolationTest.kt` from plan
003): construct an `AnchorRuntime` whose `init` block does
`emit { TestEvent.Setup }` and whose `subscriptions` block `listen`s for
`TestEvent.Setup` and reduces state. Drive it the way `ContainerViewModel`
does — call `consumeInitial()` then `subscribe()` in that order — and assert
state changed (FAILS today). Add a second test for the fixed ordering
(subscribe-await, then consumeInitial) once step 2/3 exist.

**Verify**: `./gradlew :anchor:desktopTest` → new test fails for the
documented reason (event never delivered).

### Step 2: Make subscribe() suspend until listeners are attached

In `AnchorRuntime.subscribe()`, after launching the collectors, await
attachment before returning:

```kotlin
suspend fun CoroutineScope.subscribe(): Job {
  val flows = emitter.handlerFlows()
  val job = launch {
    supervisorScope {
      flows.forEach { flow ->
        launch { flow.collect {} }
      }
    }
  }
  if (flows.isNotEmpty()) {
    _emitter.subscriptionCount.first { it >= flows.size }
  }
  return job
}
```

Import `kotlinx.coroutines.flow.first`. KDoc: "returns once every listener is
actively collecting; events emitted after this call resumes are delivered."

**Verify**: `./gradlew :anchor:desktopTest` → compiles; existing tests green.

### Step 3: Swap the order in ContainerViewModel

```kotlin
init {
  viewModelScope.launch(Dispatchers.Default) {
    safeExecute(anchor, anchor.onDomainError, anchor.defect) {
      with(anchor) {
        subscribe()
      }
      anchor.consumeInitial()
    }
  }
}
```

Ordering consequence to put in the KDoc of `ContainerViewModel`: each listener
receives `Created` (on attach) before any event emitted by `init`.

**Verify**: `./gradlew :anchor:desktopTest` → step-1 fixed-ordering test
passes; `./gradlew build` → exit 0.

## Test plan

- `init-emitted event reaches listener` (the regression case).
- `Created precedes init events` — listener records event order; assert
  `[Created, Setup]`.
- `subscribe with zero listeners returns` — runtime with no `subscriptions`
  block; `subscribe()` must not hang (guards the `flows.isNotEmpty()` branch).
- Pattern: plan 003's test file. Verification: `./gradlew :anchor:desktopTest`.

## Done criteria

- [ ] All three new tests pass; existing `:anchor` suite green
- [ ] `ContainerViewModel.init` calls `subscribe()` before `consumeInitial()`
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- Plan 003 has not landed (no `handlerFlows()`/`supervisorScope` in
  `AnchorRuntime.kt`).
- `subscriptionCount.first { ... }` hangs in tests (a listener flow may
  subscribe lazily through an intermediate operator that changes counting —
  report which operator).
- Existing tests depend on the old init-before-subscribe order (a test
  asserting events from init are NOT delivered would be such a case).

## Maintenance notes

- Residual race (accepted): a UI `execute()` that emits can in principle run
  before the init coroutine finishes attaching. In practice composition (and
  therefore any click) happens after ViewModel construction scheduled the
  attach, and the await closes the dangerous window. If this ever bites,
  the fix is gating `execute` on the same attachment signal — design decision,
  not included here.
- Plan 003's listener-restart re-attachment transiently changes
  `subscriptionCount`; the await here only matters at startup, so no interaction.
- Reviewer: confirm `Created`-triggered refresh logic in consumer apps tolerates
  running before `init` completes (it always could — init never had a
  happens-before relation to Created handling).

## Investigation results (2026-09-29)

Base: `origin/fix/023-event-emit-deadlock` (`36b1348`, PR #272). Drift check:
`AnchorRuntime.kt` changed by #239 (`edc1ee3`), `687d4e5` and `36b1348`;
`ContainerViewModel.kt` is unchanged since `492f7bc`.

- **Plan 003 shape did not land as written.** There is no `handlerFlows()` or
  `supervisorScope`. #239 introduced `handlers()` plus a `SupervisorJob` whose
  parent is the caller's job, a no-op `CoroutineExceptionHandler` and
  `launchIn(supervised)`. Step 2 was adapted to that structure, as the
  coordinator's note anticipated.
- **`subscriptionCount.first { it >= N }` (step 2 as written) is unsafe.** I
  checked it experimentally against the new tests. A handler that ends before
  it subscribes (`events.onStart { throw … }`, routed to `defect`) never
  increments the count, so `subscribe()` hangs and `init` never runs. The
  count is also a live gauge, not a per-handler latch: a handler that attaches
  and then ends (`take(1)`, an unhandled failure on `Created`) decrements it,
  and a handler that subscribes twice (`merge(events…, events…)`) inflates it.
- **Step 3 as written (one `safeExecute { subscribe(); consumeInitial() }`)
  mirrors the triage defect.** A handled domain error in subscription *setup*
  would then skip `init`. I checked this experimentally: the guard test failed
  with that ordering.
- **Init domain error skips `subscribe()` (triage defect).** Reproduced
  through a real `ContainerViewModel`. `init` raising, or throwing a handled
  defect, left no subscriptions, so a later `emit` was never handled.
- **Maintenance note correction.** Before this change, `ContainerViewModel`
  ran `init` to completion before any handler saw `Created`. That was a real
  happens-before relation. After it, `Created` handling can run before or
  concurrently with `init`, which the plan intends. Consumers' `Created`
  handlers that read state set by `init` need reviewer attention.

## Execution results (2026-09-29)

- **Branch**: `fix/004-init-event-delivery` (worktree
  `scratchpad/impl-004`), commits `cd12ba9` (🧪 failing tests) and `d628db8`
  (🐛 fix). **Not pushed, no PR.** The coordinator put the branch on hold: the
  gate is blocked because Xcode 27.0 was installed and its licence has not
  been accepted.
- **Changes**:
  - `AnchorRuntime.subscribe()` now waits until every handler has attached.
    Each handler coroutine carries a private `HandlerAttachment` context
    element. The bus's `onSubscription` completes that element's deferred
    before it emits `Created`, and the element is inherited by the coroutines
    that `flatMapLatest` and `flowOn` start. Handler job completion also
    completes the deferred. `awaitAll()` replaces the
    `subscriptionCount` wait.
  - `ContainerViewModel` runs `subscribe()` first, then `consumeInitial()`.
    Each call has its own `safeExecute`, so routing to `onDomainError` and
    `defect` is unchanged. KDoc: `Created` arrives before any `init` event.
  - `SubscriptionAnchor.emit` KDoc: the "`init` events are dropped" sentence is
    replaced by the new guarantee and its limitation.
- **Tests** (`InitEventDeliveryTest`, 9 tests, driven through a real
  `ContainerViewModel`): 6 failed before the fix, for the documented reasons.
  They covered the dropped init event, `Created`-before-`Setup`, the
  flatMapLatest handler, the handler that ends early, and init domain error
  and init defect leaving subscriptions attached. After the fix, all 9 pass on
  desktop and Android host. Ten `--rerun`s of the event-related suites on
  desktop were all green.
- **Gate**: `./gradlew build --continue`: the only failures are 34 iOS
  `link*` tasks, all with `xcrun` exit 69 (licence). Every desktop and Android
  host test, lint, and the rest of `build` pass. Test results: `anchor`
  desktop 104/104, host 100/100; `anchor-test` 73/73 and 73/73;
  `features:config` 6/6; `features:main` 6/6; `umbrella` 1/1. The iOS
  simulator tests have not run yet.
- **Deviations**:
  1. Attachment detection uses a per-handler latch instead of
     `subscriptionCount`, for the reasons above.
  2. Two `safeExecute` boundaries instead of one.
  3. Six extra guard tests beyond the plan's three.
  4. `plans/README.md` is not updated. The coordinator maintains the index.
- **Open for the maintainer**: a `connect` handler that never collects its
  event flow and never ends (for example, one that ignores `events` and
  collects an external infinite flow) keeps `init` from running. The plan's
  own design has the same limitation, and it is now documented in the KDoc.
  Options: (a) accept it and document it in `connect`/docs (plan 028);
  (b) bound the wait and then run `init` anyway; (c) no wait for handlers that
  attach asynchronously, which drops init events for `flatMapLatest` chains.

### Maintainer decision and redesign (2026-09-29, supersedes the "Open for the maintainer" item and the per-handler wait above)

- **Decision**: the maintainer chose a NO-HANG REDESIGN. `init` must never be
  blocked by a `connect()` handler that ignores its event flow and never
  completes, such as `connect<E> { repository.observe().anchor(...) }`. They
  did not want a timeout or bounded wait.
- **Verified premise (it was wrong for the common case)**: a single-threaded
  probe checked `subscriptionCount` right after `launch` returns.
  `CoroutineStart.UNDISPATCHED` alone attaches synchronously only for chains
  that start no coroutine of their own:
  - Attached: plain, `filter`/`map`/`onEach`, `filterIsInstance`, `onStart`,
    `catch`/`retry`, `scan`, `take`, `distinctUntilChanged`,
    `flatMapConcat`, `zip`.
  - Not attached: `flatMapLatest` (including the main sample's
    `filter.flatMapLatest`), `mapLatest`, `transformLatest`, `flatMapMerge`,
    `buffer`, `conflate`, `debounce`, `sample`, `combine`, `merge`,
    `channelFlow` and `produceIn(own scope)`. Each of these collects upstream
    in a separately dispatched coroutine, such as the one
    `ChannelFlow.produceImpl` starts with `CoroutineStart.ATOMIC`.
- **Design**: handlers run on a private `HandlerDispatcher` that wraps the
  caller's dispatcher. While `subscribe()` launches them,
  `isDispatchNeeded = false`, so every coroutine in a handler's tree runs in
  place through the thread-local unconfined event loop. That includes the
  coroutines operators start internally. `launch` therefore returns only when
  everything the handler started is suspended. After the launch loop,
  `starting = false` and dispatch goes to the delegate (Default) exactly as
  before. Nothing waits on attachment, so a handler that never subscribes
  cannot block. In the same probe, this attached every operator listed above.
- **Contract (the `emit` KDoc and the `subscribe()` KDoc)**: a handler that
  collects its event flow in its own coroutines is attached before `init`
  runs and receives init events after its `Created`. A handler attaches later,
  and misses the events emitted before then, when its subscription waits on:
  - `flowOn` to another dispatcher. The probe showed this is a real
    cross-thread race, and the test makes it deterministic with a held
    dispatcher.
  - An outside scope, such as `shareIn`/`stateIn`/`produceIn`.
  - Asynchronous work before it collects: `onStart { effect { … } }`, a
    delay, or waiting on another flow.
- **Residual edges**:
  - A handler that does blocking or CPU work before it first suspends now
    delays `init` by that much. An infinite loop that never suspends, or a
    `yield()` loop, would block it. Before, such a handler only hogged a
    Default thread.
  - A handler coroutine resumed from another thread during the
    microsecond-scale launch window runs in place on that thread.
  - An event that a `Created`-triggered action emits during startup reaches
    only the handlers already launched. This was already true.
  - `subscribe()` assumes its caller is not inside an active unconfined
    loop. `ContainerViewModel` calls it from a dispatched Default task.
- **Tests**: `InitEventDeliveryTest` now has 12 cases, all run through a real
  `ContainerViewModel`. The three new ones are in commit `7c4de20`:
  - A handler that never collects its events does not block `init`. It
    failed on `d628db8` (init never ran).
  - A `buffer()` handler receives `[Created, Setup]`.
  - A `flowOn(held)` handler: `init` is not blocked, and the handler
    subscribes only when its dispatcher runs, then receives
    `[Created, Ping]`, missing `Setup`. On `d628db8` it failed because `init`
    was blocked.

  All 12 pass on desktop, Android host and the iOS simulator. The
  event-related suites passed 20 of 20 `--rerun` runs on desktop.
- **Commits**: `7c4de20` (🧪 hang reproduction plus the buffer and flowOn
  tests) and `0048d11` (♻️ in-place startup dispatcher, KDoc contract).
  `HandlerAttachment`/`awaitAll` are gone, and `onSubscription` is back to
  `emit(Created)`.
- **Gate**: the Xcode 27.0 license was accepted during this run.
  `./gradlew build --continue` then passed completely, BUILD SUCCESSFUL,
  with no xcrun errors. Test counts:

  | Module | desktop | Android host | iOS simulator |
  |---|---|---|---|
  | `anchor` | 107/107 | 103/103 | 103/103 |
  | `anchor-test` | 73/73 | 73/73 | 73/73 |
  | `features:config` | — | 6/6 | 6/6 |
  | `features:main` | — | 6/6 | 6/6 |
  | `features:counter` | — | — | 4/4 |
  | `umbrella` | — | 1/1 | 2/2 |

  Per the coordinator, the branch is still **not pushed** and there is no PR.
  The worktree is kept.
