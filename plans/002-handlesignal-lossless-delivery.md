# Plan 002: Make HandleSignal deliver every signal (no conflation, no handler cancellation)

> **Triage note (2026-09-29, origin/master `0bc430c`)**: Premise re-confirmed at `0bc430c` by headless Recomposer probes from the #266 verifier: a burst of 3 signals delivers only the last, a slow handler is cancelled by the next signal, and signals are dropped before the first frame and below STARTED. Plan 024 (producer side, #266) depends on this plan for its Compose wiring step.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt anchor-compose/src/commonTest/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: plans/001-anchor-compose-test-harness.md
- **Category**: bug
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

Signals are Anchor's one-time UI events (toasts, navigation). The runtime
buffers up to 64 of them (`AnchorRuntime.kt:58-59`:
`MutableSharedFlow(extraBufferCapacity = 64)`), but the Compose consumer
collapses the stream into a single "latest value" Compose `State` and handles
it from a restartable `LaunchedEffect`. Two concrete failure modes follow:

1. **Conflation**: if two signals are emitted before the composition processes
   the first, only the latest is handled — the rest are silently dropped.
2. **Handler cancellation**: `LaunchedEffect(signals)` restarts when the next
   signal arrives, cancelling a suspend handler that is still running (e.g. a
   snackbar being shown) mid-flight.

For a state-management library whose pitch is type-safe, reliable event
delivery, this is a core-contract bug.

## Current state

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt:41-52`
  (the whole consumer):

  ```kotlin
  @Suppress("ModifierRequired")
  @Composable
  public inline fun <reified T : Signal> HandleSignal(
    noinline block: @DisallowComposableCalls suspend (T) -> Unit,
  ) {
    val signals by LocalSignals.current.collectAsStateWithLifecycle(null)
    val update = rememberUpdatedState(block)
    LaunchedEffect(signals) {
      val signal = signals?.provide()
      if (signal is T) {
        update.value(signal)
      }
    }
  }
  ```

- `LocalSignals` is defined in the same file (lines 19-21) as
  `ProvidableCompositionLocal<Flow<SignalProvider>>` defaulting to `emptyFlow()`.
- The producer side, `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:56-59,208-213`:

  ```kotlin
  internal val _signals: MutableSharedFlow<SignalProvider> =
    MutableSharedFlow(extraBufferCapacity = 64)
  ...
  override suspend fun post(
    block: SignalScope.() -> Signal,
  ) {
    val signal = SignalScope.block()
    _signals.emit(SignalProvider { signal })
  }
  ```

- The module already uses `androidx.lifecycle.compose.collectAsStateWithLifecycle`
  (see import in `RememberAnchor.kt:9`), so the multiplatform
  `lifecycle-runtime-compose` APIs (`LocalLifecycleOwner`, `repeatOnLifecycle`)
  are available on the classpath via `libs.lifecycle.runtime` /
  `libs.lifecycle.viewmodel`.
- Repo conventions: explicit API mode, KDoc on all public APIs, 2-space indent.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Module tests | `./gradlew :anchor-compose:desktopTest` | exit 0, all pass |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt`
- `anchor-compose/src/commonTest/kotlin/dev/kioba/anchor/compose/HandleSignalTest.kt` (create)
- `anchor-compose/build.gradle.kts` (only if an explicit
  `lifecycle-runtime-compose` dependency must be made direct instead of transitive)

**Out of scope**:
- `AnchorRuntime._signals` configuration (replay/buffer changes alter delivery
  semantics for *all* collectors — see Maintenance notes).
- The pre-subscription drop (signals posted from `init` before the first
  composition collects are lost because `replay = 0`). Document it; do not fix
  it here — a replay-based fix would re-deliver stale signals to every new
  `HandleSignal` and is a separate design decision.
- `RememberAnchor.kt`, `AnchorAction.kt`, `LocalScope.kt`.

## Git workflow

- Branch: `fix/handlesignal-lossless-delivery`
- Commit style: gitmoji, e.g. `🐛 Collect signals as a stream in HandleSignal`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Failing tests first

In `anchor-compose/src/commonTest/.../HandleSignalTest.kt` (using the plan-001
harness and fixtures), add:

1. `burst of signals are all handled` — an action that posts 3 distinct
   signals back-to-back (`post { Toast(1) }; post { Toast(2) }; post { Toast(3) }`);
   assert the handler eventually received `[1, 2, 3]` (order preserved).
2. `slow handler is not cancelled by next signal` — handler body:
   `delay(100); completed += it.n`. Post two signals quickly. Assert both
   complete (`completed.size == 2` within a generous `waitUntil`).
3. `signals of other types are ignored` — post a non-`Toast` signal type and a
   `Toast`; assert only the `Toast` was handled.

Run them — tests 1 and 2 are expected to FAIL against current code (that
demonstrates the bug). If they *pass* reliably, STOP: the conflation analysis
no longer holds, report findings.

**Verify**: `./gradlew :anchor-compose:desktopTest` → tests 1-2 fail, 3 passes.

### Step 2: Rewrite HandleSignal to collect the stream directly

Replace the body of `HandleSignal` in `LocalSignal.kt`:

```kotlin
@Suppress("ModifierRequired")
@Composable
public inline fun <reified T : Signal> HandleSignal(
  noinline block: @DisallowComposableCalls suspend (T) -> Unit,
) {
  val signals = LocalSignals.current
  val update = rememberUpdatedState(block)
  val lifecycleOwner = LocalLifecycleOwner.current
  LaunchedEffect(signals, lifecycleOwner) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
      signals.collect { provider ->
        val signal = provider.provide()
        if (signal is T) {
          update.value(signal)
        }
      }
    }
  }
}
```

Imports to add: `androidx.lifecycle.Lifecycle`,
`androidx.lifecycle.compose.LocalLifecycleOwner`,
`androidx.lifecycle.repeatOnLifecycle`. Imports to remove:
`collectAsStateWithLifecycle`, `getValue` (if now unused).

Semantics this preserves/changes (also update the KDoc to state these):
- Every emission is processed, in order; a slow handler suspends the collector
  (back-pressure into the runtime's 64-slot buffer) instead of being cancelled.
- Collection pauses below `STARTED` and resumes on restart — same lifecycle
  window `collectAsStateWithLifecycle` used.
- Known limitation (KDoc note): signals posted before the first composition
  collects (e.g. from `init`) are not delivered (`replay = 0` upstream).

**Verify**: `./gradlew :anchor-compose:desktopTest` → all tests incl. step-1
tests pass.

### Step 3: Full build + lint

**Verify**: `./gradlew build` → exit 0.

## Test plan

See Step 1 (three new tests, written first). Pattern: plan-001's
`RememberAnchorTest.kt`. Verification: `./gradlew :anchor-compose:desktopTest`
→ all pass, including 3 new.

## Done criteria

- [ ] `./gradlew :anchor-compose:desktopTest` exits 0; burst + slow-handler tests pass
- [ ] `LocalSignal.kt` no longer calls `collectAsStateWithLifecycle`
- [ ] KDoc on `HandleSignal` documents ordering, back-pressure, lifecycle window,
      and the pre-subscription limitation
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- `LocalLifecycleOwner` or `repeatOnLifecycle` are unresolved in `commonMain`
  (report the lifecycle artifact/version from `gradle/libs.versions.toml`;
  the fallback design — plain `signals.collect` without lifecycle gating —
  changes background behavior and needs an advisor decision).
- Step-1 tests pass against unmodified code (analysis drift).
- Making tests pass appears to require changing `AnchorRuntime` (out of scope).

## Maintenance notes

- If the maintainer later wants `init`-posted signals delivered, the fix is on
  the producer side (e.g. replay with consume-once semantics or a Channel-backed
  design) and must consider multiple simultaneous `HandleSignal` collectors —
  a Channel would deliver each signal to only ONE collector, breaking fan-out.
- Reviewer: scrutinize lifecycle behavior on Android (rotation: collector
  detaches at `ON_STOP`, signals emitted while stopped sit in the 64-buffer and
  deliver on restart — that is an improvement, but confirm it's wanted).
- `iosMain`'s `NativeSharedFlow.collect` has no equivalent loss (direct
  collection), so no iOS change is needed.

## Execution results (2026-09-29)

- **Branch**: `fix/002-handlesignal-lossless-delivery` @ `3fd47b8` (base `test/001-anchor-compose-harness` @ `4aff2b0`). Pushed by the coordinator as PR #275, stacked on #274.
- **Worktree**: scratch worktree, removed after the push
- **Drift check**: `LocalSignal.kt` was unchanged since `492f7bc`, and the "Current state" excerpt matched. `anchor-compose/src/commonTest/` doesn't exist. The harness lives in `desktopTest`.
- **Change**: `HandleSignal` now collects `LocalSignals.current` inside `LaunchedEffect(signals, lifecycleOwner) { lifecycleOwner.repeatOnLifecycle(STARTED) { signals.collect { ... } } }`, as in Step 2. It no longer calls `collectAsStateWithLifecycle`. `LocalLifecycleOwner` and `repeatOnLifecycle` resolve in `commonMain` through the existing `libs.lifecycle.runtime` dependency, so `build.gradle.kts` is unchanged. The KDoc documents ordering, one-at-a-time handling with back-pressure (a 64-slot buffer, after which `post` suspends), the STARTED window (a handler still running at ON_STOP is cancelled and not re-run), the latest-`block` rule, and the known limitation.
- **Deviations**:
  1. `HandleSignalTest` is in `desktopTest`, not `commonTest`, next to plan 001's harness.
  2. The slow-handler test holds handler 1 on a `CompletableDeferred` gate, not `delay(100)`. The effect dispatcher runs on virtual time, and the gate makes "the next signal arrives while the handler is still running" deterministic. It also keeps the test separate from conflation.
  3. The tests wait for the collector to attach or detach through a test-only `onSubscription` counter wrapped around `LocalSignals`, so no signal is posted before a collector exists.
  4. A 4th test, `collection stops below STARTED and resumes on restart`, pins the lifecycle window and asserts `[1, 3]`.
  5. `plans/README.md` was not updated, per coordinator instructions.
- **Maintenance-note correction**: the note above ("signals emitted while stopped sit in the 64-buffer and deliver on restart") is **wrong**. Below STARTED the collector unsubscribes (`subscriptionCount` 0). With `replay = 0` and no subscriber, `SharedFlow` drops the signal: `extraBufferCapacity` only applies while a subscriber exists. Plan 001's probe saw `[1, 3]` on the old code, and the new test asserts `[1, 3]` with the fix. The KDoc says such signals are "dropped, not buffered". Holding them until a collector attaches is plan 024's job (#266). When 024 lands, it should flip that assertion to `[1, 2, 3]` and drop the KDoc's known-limitation paragraph.
- **Test evidence**:
  - Before the fix (3/3 runs): `burst of signals are all handled` fails with `expected:<[1, 2, 3]> but was:<[3]>`, and `slow handler is not cancelled by next signal` fails with `expected:<[1, 2]> but was:<[2]>`. The type-filter and lifecycle tests pass.
  - After the fix: `./gradlew :anchor-compose:desktopTest` passes 15/15 (HandleSignal 4, RememberAnchor 5, NestedAnchor 6). It also passed 20 consecutive `--rerun` runs.
  - Sensitivity check (scratch, reverted): with `repeatOnLifecycle` replaced by plain collection, the lifecycle test fails (`Condition (collector detached) still not satisfied after 5000 ms`).
  - `./gradlew build`: BUILD SUCCESSFUL (646 tasks). `xcrun` was working by the gate, so this covers the iOS links, `iosSimulatorArm64Test`, Android host tests and lint.
- **PR body**: used verbatim for PR #275
