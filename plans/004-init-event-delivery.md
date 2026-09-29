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
