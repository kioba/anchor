# Plan 023: Stop `emit {}` from a `connect()` handler's action from wedging the event bus

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt anchor/src/commonTest/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition. (Plan 004 landing is expected drift:
> re-read `subscribe()`/`consumeInitial()` and continue if `_emitter`'s
> declaration and `emit` are unchanged.)

## Status

- **Priority**: P1
- **Effort**: S (fix) + S (investigation)
- **Risk**: MED. It changes `emit {}` back-pressure semantics. The API is unchanged.
- **Depends on**: none. Coordinate with plan 004 (same file) and 028 (docs and pinning test 6).
- **Category**: bug
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: [`plans/specs/023-event-emit-deadlock.md`](specs/023-event-emit-deadlock.md)
- **Issue**: found under https://github.com/kioba/anchor/issues/140. Recommend a dedicated issue.
- **Gating**: Investigate-then-Act. Phase A (tests + prototypes) → **mandatory STOP for maintainer GO** on spec Q1/Q2 → Phase B (implement).

## Why this matters

Events are documented as the way "*one action triggers another*" (`docs/concepts.md:88`). But the bus is an unbuffered `MutableSharedFlow()`, and `.anchor {}` runs actions inline in each handler's collector. If such an action (or an `onDomainError`/`defect` handler invoked from it) calls `emit {}`, the emission waits for the handler that is emitting it.

The result is permanent and silent: **every later `emit {}` on that anchor suspends forever**. Probe P1 on `0bc430c` showed that an unrelated emit timed out and a second Ping timed out (spec §1.2). A one-line bounded buffer fixes it, and the prototype passed all 95 existing `:anchor` desktop tests. Choosing between bounded and unbounded changes `emit`'s back-pressure contract, though, so the maintainer decides.

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:62-64`:

  ```kotlin
  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _emitter: MutableSharedFlow<Event> = MutableSharedFlow()
  ```

- `AnchorRuntime.kt:85-88`:

  ```kotlin
  private val emitter: SharedFlow<Event> =
    _emitter
      .asSharedFlow()
      .onSubscription { emit(Created) }
  ```

- `AnchorRuntime.kt:229-233`:

  ```kotlin
  override suspend fun emit(
    block: SubscriptionScope.() -> Event,
  ): Unit =
    _emitter
      .emit(SubscriptionScope.block())
  ```

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt:46-53`:

  ```kotlin
  public fun <I> Flow<I>.anchor(
    action: suspend Anchor<R, S, Err>.(I) -> Unit,
  ): Flow<I> =
    onEach { value ->
      safeExecute(anchor, onDomainError, defect) {
        anchor.action(value)
      }
    }
  ```

- KDoc on `SubscriptionAnchor.emit`, `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:239-257`: "Emits an internal event." It says nothing about delivery.
- The test pattern for driving subscriptions is `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionDslTest.kt:74-123`:
  - `AnchorRuntime(..., subscriptions = { connect<...> { ... } })`;
  - `launch { with(anchor) { supervisorReady.complete(subscribe()) } }`;
  - wait on `anchor._emitter.subscriptionCount`;
  - `supervisor.cancelAndJoin()`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Regression tests | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.EventBusReentrancyTest'` | Phase A: the two regression tests FAIL, the guards pass. Phase B: all pass |
| Core suite | `./gradlew :anchor:desktopTest` | exit 0 |
| Native | `./gradlew :anchor:iosSimulatorArm64Test` | exit 0 (no signal-6 abort) |
| Features | `./gradlew :features:main:desktopTest` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt`: the `_emitter` declaration, plus an optional private capacity constant
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt`: KDoc on `SubscriptionAnchor.emit` only
- `anchor/src/commonTest/kotlin/dev/kioba/anchor/EventBusReentrancyTest.kt` (create)
- If 028 has landed: `docs/guarantees.md` (Events section), `docs/best-practices.md` (remove the "don't emit from handlers" item), `docs/llms-full.txt` (regenerated), and the 028 pinning test 6 in `DocumentedBehaviorTest.kt`

**Out of scope**:
- `SubscriptionDsl.kt` behaviour (option (d) was rejected).
- `subscribe()`/`consumeInitial()` ordering (plan 004).
- `_signals` (#266).
- Making the >64 self-emission case fail fast (spec Q3). Only document it.

## Git workflow

- Branch: `fix/event-bus-reentrant-emit`
- Commit style: gitmoji, for example `🧪 Reproduce event bus deadlock on reentrant emit` then `🐛 Buffer the event bus so handler actions can emit`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A — reproduce and prototype (no production change is committed)

### Step 1: Failing regression tests first

Create `EventBusReentrancyTest.kt` in the style of `SubscriptionDslTest`. Use `runBlocking`, `withTimeout` guards and `CompletableDeferred`, and **always cancel the supervisor and its scope in `finally`**. That releases the stuck coroutines and avoids a Native abort.

Local fixture events: `Ping`, `Pong`, `Other` (sealed interface extending `Event`).

1. **`an action run by a connect handler can emit without blocking later emits`**:
   - Subscriptions: `connect<Ping> { it.anchor { emit { Pong } } }` and `connect<Pong> { it.onEach { pongs += 1 } }`.
   - Emit `Ping`.
   - Then `withTimeout(2_000) { anchor.emit { Other } }` and `withTimeout(2_000) { anchor._emitter.emit(Ping) }`.
   - Assert `pongs == 2` eventually.
2. **`onDomainError can emit for an error raised in a handler action`**:
   - Set `onDomainError = { emit { Pong } }`, and use `connect<Ping> { it.anchor { raise(TestError.NotFound) } }` plus the Pong counter.
   - Emit `Ping`, then `withTimeout(2_000) { anchor.emit { Other } }`.
3. **Guard — `each handler receives events in emission order`**: one handler records 100 `Seq(i)` events emitted from outside. Assert the list is `0..99`.
4. **Guard — `every handler receives every event`**: two `connect<Seq>` handlers, and 10 events. Both lists are `0..9`.
5. **Guard — `each handler receives Created once`**.

**Verify**: the regression command shows tests 1 and 2 FAILING with `TimeoutCancellationException`, and tests 3–5 passing. If test 1 passes on unmodified code, STOP.

### Step 2: Prototype both options (do not commit)

1. Option (a): `MutableSharedFlow(extraBufferCapacity = 64)`. Run the regression command and the core suite, and record the results.
2. Option (b): `extraBufferCapacity = Int.MAX_VALUE`. Run the same commands and record.
3. With (a), add a throwaway test in which one handler action emits 65 events to a bus it is subscribed to. Record whether it wedges; the spec predicts it does. This is the documented limit.
4. Run `./gradlew :anchor:iosSimulatorArm64Test` with (a).

**Verify**:
- (a) makes tests 1–5 pass. The spec's prototype also saw 95/95 in the existing suite.
- The results table (option × test) is written into the PR description or your report.
- `git diff` of production code is reverted afterwards (`git diff --stat -- anchor/src/commonMain` is empty).

### STOP — maintainer GO required

Report the Step 2 table and the answers needed for spec Q1 (bounded or unbounded), Q2 (capacity) and Q4 (release). **Do not start Phase B without an explicit GO naming the option.**

### Phase B — implement the chosen option

### Step 3: Implement

Apply the chosen `_emitter` declaration. For (a), use a named constant, for example `private const val EVENT_BUFFER_CAPACITY = 64`, next to the class.

**Verify**: the regression command exits 0, with all 5 tests passing.

### Step 4: Document the contract

- KDoc on `SubscriptionAnchor.emit` (`Anchor.kt:239-257`) must say three things:
  - the event is delivered to every `connect` handler attached at the time; events emitted while no handler is attached, for example from `init` before plan 004, are dropped;
  - `emit` returns once the event is queued for all handlers. With (a), it suspends only when 64 events are already pending behind the slowest handler;
  - with (a), a single handler invocation that emits more than 64 events to itself can still block.
- If 028 has landed:
  - update `guarantees.md#events`: remove the "Known issue" admonition and state the new back-pressure rule;
  - remove the best-practices item;
  - flip `DocumentedBehaviorTest` test 6 to assert that `emit` returns while a handler is busy;
  - regenerate `docs/llms-full.txt`.

**Verify**:
- `./gradlew :anchor:desktopTest` exits 0.
- If docs changed: `bash scripts/generate-llms-full.sh` twice with no further diff.

### Step 5: Native, features, full build

**Verify**: `./gradlew :anchor:iosSimulatorArm64Test`, `./gradlew :features:main:desktopTest` and `./gradlew build` all exit 0.

## Test plan

- **Test-first**: Step 1 tests 1–2 reproduce the wedge (a timeout) on `0bc430c` (spec probe P1). Tests 3–5 guard the ordering, fan-out and `Created` semantics that the fix must preserve.
- Pattern: `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionDslTest.kt`.
- Regression: the full `:anchor` desktop and iOS-simulator suites (95 desktop tests at `0bc430c`), `features/main`, and `./gradlew build`.

## Done criteria

- [ ] `EventBusReentrancyTest` exists, and tests 1–2 failed before the fix. Record the output in the PR.
- [ ] The maintainer GO is recorded, naming option (a) or (b)
- [ ] All 5 tests pass. `:anchor:desktopTest`, `:anchor:iosSimulatorArm64Test` and `./gradlew build` exit 0
- [ ] The `emit` KDoc states the contract (and the limit, for (a))
- [ ] If 028 has landed, its Events docs and pinning test 6 are updated in the same PR
- [ ] The `plans/README.md` status row is updated

## STOP conditions

Stop and report back if:

- Step 1 test 1 passes on unmodified code (the analysis has drifted).
- Either option breaks an existing test other than 028's pinning test 6. Report which one; it may encode the rendezvous semantics on purpose.
- A Native run aborts (signal 6). A test leaked a failing coroutine; fix the test's cleanup, not production code.
- The maintainer GO has not been given at the Phase A/B boundary.
- Plan 004 has changed `_emitter` or `emit` in a way that conflicts with the excerpts.

## Maintenance notes

- If the maintainer later wants the >64 self-emission case to fail fast (spec option (e)), it needs a context-element marker on handler collectors. Keep it debug-only; a throw would abort on Native.
- Reviewer: check that per-handler order is still FIFO (test 3), and that no production code now launches coroutines without a `CoroutineExceptionHandler`.
- Plan 004 will reorder `subscribe()` before `init`. Once both land, events emitted from `init` are delivered and buffered. Re-read the KDoc wording about "no handler attached".

## Investigation results (Phase A, 2026-09-29)

Executed in worktree branch `fix/023-event-emit-deadlock` off `origin/master` = `0bc430c`.

- **Drift check**: `git diff --stat 0bc430c..HEAD -- <in-scope files>` is empty, because `origin/master` is still `0bc430c`. The live `_emitter` declaration (`AnchorRuntime.kt:62-64`), `emitter`/`onSubscription` (`:85-88`), `emit` (`:229-233`), `.anchor {}` (`SubscriptionDsl.kt:46-53`) and the `emit` KDoc all match "Current state". Plan 004 has not landed. Plan 028 has not landed either: there is no `docs/guarantees.md`, `docs/best-practices.md` or `DocumentedBehaviorTest`, so the 028 items in Step 4 do not apply.
- **Minor count drift**: `:anchor:desktopTest` has **90** tests at `0bc430c`, not 95. With the 5 new tests it is 95. The spec's "95/95" prototype run most likely included its probe tests. This is not a STOP condition.
- **Step 1** (unmodified code, `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.EventBusReentrancyTest'`): 5 tests, 2 failed.
  - Test 1 (`an action run by a connect handler can emit without blocking later emits`) FAILED with `TimeoutCancellationException` after 2.02 s, at the unrelated `emit { Other }`.
  - Test 2 (`onDomainError can emit for an error raised in a handler action`) FAILED with `TimeoutCancellationException` after 2.01 s, at `emit { Other }`.
  - Guards 3–5 (FIFO order for 100 events; fan-out to 2 handlers × 10 events; `Created` once and first per handler) PASSED.
  - Test commit: `5fb8761 🧪 Reproduce event bus deadlock on reentrant emit`.
- **Step 2** (prototypes, not committed; production diff reverted afterwards, `git diff --stat -- anchor/src/commonMain` empty):

  | Option | Regression tests 1–2 | Guards 3–5 | `:anchor:desktopTest --rerun` | 64 self-emits in one handler invocation | 65 self-emits | `:anchor:iosSimulatorArm64Test` |
  |---|---|---|---|---|---|---|
  | none (`0bc430c`) | FAIL (timeout) | pass | only the new class was run: 3/5 (tests 1–2 fail) | — | — | not run |
  | (a) `extraBufferCapacity = 64` | pass | pass | 95/95 | ok | **wedges** (65th `emit` suspends; a later outside `emit` times out) | 95/95, including 4 throwaway probes; no signal 6 |
  | (b) `extraBufferCapacity = Int.MAX_VALUE` | pass | pass | 95/95 | ok | ok | not run |

  - The limit is shared. In a probe variant where the test body buffered one extra event before the handler ran, the handler wedged after **63** self-emits. The 64 slots count *every* event pending behind the slowest handler, not only that invocation's own emits. The KDoc should say "pending", not "emitted by this handler".
  - A draining sibling handler does not change the (a) limit. The busy handler is itself the slowest collector.
- **No existing test broke** under (a) or (b), and no STOP condition was hit.
- **Maintainer GO** (relayed 2026-09-29): Q1 is (a), bounded with 64 slots. Q2 is a fixed internal constant, not configurable. Q3 is no debug detector; the limit is documented only. Q4 is to ship in 0.1.9. Proceeding to Phase B.

## Execution results (2026-09-29)

- **Branch / PR**: `fix/023-event-emit-deadlock` → https://github.com/kioba/anchor/pull/272 (base `master`, from `origin/master` = `0bc430c`).
- **Commits**:
  - `5fb8761 🧪 Reproduce event bus deadlock on reentrant emit`: `EventBusReentrancyTest`, with 2 regression tests and 3 guards.
  - `36b1348 🐛 Buffer the event bus so handler actions can emit`: `private const val EVENT_BUFFER_CAPACITY: Int = 64` above the class; `_emitter = MutableSharedFlow(extraBufferCapacity = EVENT_BUFFER_CAPACITY)`; the `SubscriptionAnchor.emit` KDoc covers delivery to attached handlers in order, dropping with no handler attached (e.g. from `init`), return-on-queue with suspension only at 64 pending, and the >64 self-emission limit ("fewer if other events are already pending"). `_signals` is untouched.
- **Option implemented**: (a), per the maintainer GO: fixed at 64, not configurable, no debug detector, 0.1.9.
- **Test evidence**:
  - Before the fix: the regression class had 5 tests with 2 failing (tests 1–2 hit `TimeoutCancellationException` at the later `emit { Other }`).
  - After the fix, `EventBusReentrancyTest` passes 5/5.
  - `:anchor:desktopTest --rerun`: 95/95.
  - `:anchor:iosSimulatorArm64Test --rerun`: 91/91, no signal 6.
  - `:features:main:allTests --rerun`: 12/12.
  - `./gradlew build`: exit 0. The first attempt hit `No space left on device` on the machine, in the 8 iOS `linkReleaseFramework*` tasks only. I freed my worktree's debug frameworks and ran those 8 with `--max-workers=1`, and they passed. A final `./gradlew build` then passed.
- **Deviations**:
  - Branch name is `fix/023-event-emit-deadlock`, as the operator instructed, instead of `fix/event-bus-reentrant-emit`.
  - `:features:main:desktopTest` doesn't exist (no desktop target), so I ran `:features:main:allTests`.
  - The desktop baseline is 90 tests, not 95.
  - Plan 028 hasn't landed, so no docs or pinning-test changes were made.
  - `plans/README.md` was not updated, per operator rules; the maintainer should update the 023 row.
- **Follow-ups**:
  - 028's pinning test 6 and `guarantees.md#events` flip once this lands, and are noted in the PR.
  - After plan 004 lands, re-read the KDoc sentence about `init` events being dropped.
  - Consider opening a dedicated issue, since the spec recommends one.
  - PR CI will fail at "Setup Android SDK" until #268 merges.
