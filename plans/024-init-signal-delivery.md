# Plan 024: Hold signals posted before an accepting collector attaches (#266)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. This plan is **Investigate-then-Act**: Phase A
> ends in a mandatory STOP; do not start Phase B without an explicit
> maintainer **GO** that names the chosen option (the spec's recommendation is
> option (b′)). When done, update the status row for this plan in
> `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt anchor/src/iosMain/kotlin/dev/kioba/anchor/ anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/`
> Expected drift: plan 002 rewrites `LocalSignal.kt` (and plan 004 touches
> `AnchorRuntime.subscribe()` / `ContainerViewModel.init`). Anything that changes
> `_signals`, `post`, `AnchorSink.signals` or `nativeSignals()` is a STOP — compare
> the "Current state" excerpts against live code first.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED (core runtime delivery semantics + concurrency; mitigated by a prototype that passed a 200×200 stress test)
- **Depends on**: plans/002-handlesignal-lossless-delivery.md (Compose step only; 002 in turn depends on plans/001-anchor-compose-test-harness.md). Supersedes plans/017-signal-semantics-design.md. Complements plans/004-init-event-delivery.md.
- **Category**: bug
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: [plans/specs/024-init-signal-delivery.md](specs/024-init-signal-delivery.md)

## Why this matters

`post {}` emits into a `MutableSharedFlow` with `replay = 0`. With no subscriber,
SharedFlow discards the value regardless of `extraBufferCapacity` (kotlinx.coroutines
`SharedFlow` KDoc: "*In the absence of subscribers only the most recent `replay`
values are stored and the buffer overflow behavior is never triggered*").
`ContainerViewModel` runs `init` on `Dispatchers.Default` the moment it is
constructed, while `HandleSignal` attaches only after the first composition is
applied. So signals posted from `init` — including the library's own recommended
`defect = { post { ErrorSignal(it) } }` pattern for init failures — are lost
nondeterministically, and signals posted during rotation or while backgrounded
(below `STARTED`) are lost too. Issue #144 was closed as fixed by #152, but #152
only added `extraBufferCapacity`. The issue's reproducer fails on `0bc430c`:
`java.lang.AssertionError: Signal posted from init was dropped: no subscriber, replay = 0`.

## Current state

- Producer — `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:57-60,83,222-227`:

  ```kotlin
  @PublishedApi
  @Suppress("ktlint:standard:backing-property-naming", "PropertyName")
  internal val _signals: MutableSharedFlow<SignalProvider> =
    MutableSharedFlow(extraBufferCapacity = 64)
  ...
  override val signals: SharedFlow<SignalProvider> = _signals.asSharedFlow()
  ...
  override suspend fun post(
    block: SignalScope.() -> Signal,
  ) {
    val signal = SignalScope.block()
    _signals.emit(SignalProvider { signal })
  }
  ```

- Public surface — `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:61-66,75-89`:
  `public fun interface SignalProvider { public fun provide(): Signal }` and
  `public abstract class AnchorSink<R, S, Err>` with
  `public abstract val signals: SharedFlow<SignalProvider>` (line 88). Only
  `AnchorRuntime` extends `AnchorSink` in this repo.

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt:30-31,44-53`:

  ```kotlin
  public val signals: Flow<SignalProvider>
    get() = anchor.signals
  ...
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

- Compose consumer — `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt:19-21,41-52`
  (pre-plan-002 shape; after 002 it collects directly under `repeatOnLifecycle`):

  ```kotlin
  @PublishedApi
  internal val LocalSignals: ProvidableCompositionLocal<Flow<SignalProvider>> =
    staticCompositionLocalOf { emptyFlow() }
  ...
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

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt:157-171`
  creates the ViewModel via `viewModel(...)` and provides
  `LocalSignals provides signalFlow` where `signalFlow = remember(anchorScope) { anchorScope.signals }`.
  `anchor-compose` uses only the **public** API of `anchor` (no friend paths, no
  `@PublishedApi` crossing modules) — any new runtime entry point it calls must be public.

- iOS — `anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt:56-57`
  (`nativeSignals(): NativeSharedFlow<SignalProvider> = NativeSharedFlow(signals)`) and
  `anchor/src/iosMain/kotlin/dev/kioba/anchor/NativeFlows.kt:64-67`
  (`collect` launches on `Dispatchers.Main` in a fresh `SupervisorJob` scope). No change
  needed there if `signals` keeps its `SharedFlow` type.

- Docs that describe signals without any delivery guarantee: `docs/concepts.md:68-84`,
  `docs/compose.md:106-123`. `docs/llms-full.txt` is generated by
  `scripts/generate-llms-full.sh` and CI (`.github/workflows/pr_check.yml` `llms_check`)
  fails if it is stale.

- Kotlin/Native constraint (commit `687d4e5`): any fire-and-forget coroutine
  under a `SupervisorJob` without a `CoroutineExceptionHandler` aborts the process
  on Native. The design below launches no coroutines of its own.

- Repo conventions: explicit API mode, warnings-as-errors, 2-space indent, KDoc on
  public API, backtick test names, `kotlin.test`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Reproducer | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.InitSignalDeliveryTest'` | Phase A: 1 failed (bug); Phase B: pass |
| Bus tests | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.internal.SignalBusTest'` | exit 0 |
| Core suite | `./gradlew :anchor:desktopTest` | exit 0 |
| Native run of common tests | `./gradlew :anchor:iosSimulatorArm64Test` | exit 0 (no signal-6 abort) |
| iOS compile | `./gradlew :anchor:compileKotlinIosSimulatorArm64` | exit 0 |
| Compose tests | `./gradlew :anchor-compose:desktopTest` | exit 0 |
| Docs regen | `bash scripts/generate-llms-full.sh` | `docs/llms-full.txt` updated |
| Full gate | `./gradlew build` | exit 0 |

## Scope

**In scope** (Phase B):
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/SignalBus.kt` (create)
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt` (signals wiring only)
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt` (add `signalsMatching` only — do NOT touch `init` ordering)
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt` (KDoc of `SignalAnchor.post` and `AnchorSink.signals` only)
- `anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt` (KDoc of `nativeSignals` only)
- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt`, `RememberAnchor.kt` (signal source wiring)
- Tests: `anchor/src/commonTest/kotlin/dev/kioba/anchor/InitSignalDeliveryTest.kt`,
  `anchor/src/commonTest/kotlin/dev/kioba/anchor/internal/SignalBusTest.kt` (create);
  `anchor-compose/src/commonTest/.../HandleSignalTest.kt` (extend — exists after plan 002)
- `docs/concepts.md`, `docs/compose.md`, `docs/llms-full.txt` (regenerated)

**Out of scope**:
- `HandleSignal` conflation / handler cancellation — plan 002 (this plan edits only the flow source inside 002's loop).
- `ContainerViewModel.init` ordering and the "init raise skips `subscribe()`" defect (spec §1.8) — plan 004 / separate issue.
- `_emitter` / `Event` delivery — plan 004.
- Swift sample (`iosApp/`) `@Published` conflation — plan 019.
- `anchor-test` (`AnchorTestRuntime`) — does not model delivery; plan 006 documents the divergence.
- Making the held capacity configurable in `create(...)` (spec Q5) unless the GO says so.

## Git workflow

- Branch: `fix/266-hold-undelivered-signals`
- Commit style: gitmoji, e.g. `🧪 Add failing tests for signals posted before a collector attaches`, then
  `🐛 Hold signals until an accepting collector attaches`, then `📝 Document signal delivery guarantees`.
- Do NOT push or open a PR unless the operator instructed it.

## Phase A — Investigate (no production changes)

### Step A1: Drift check and reproduce

Run the drift check. Create a scratch worktree
(`git worktree add <scratch>/wt-266 HEAD`), add the issue's `InitSignalDeliveryTest`
verbatim (copy it from the spec §1.2 / issue body) to
`anchor/src/commonTest/kotlin/dev/kioba/anchor/`, and run the reproducer command.
Remove the worktree afterwards (`git worktree remove --force <scratch>/wt-266`).

**Verify**: the reproducer fails with
`Signal posted from init was dropped: no subscriber, replay = 0`. If it passes,
STOP (someone already changed delivery semantics — reconcile with the spec).

### Step A2: Check prerequisites

- Is plan 002 merged? (`grep -n "collectAsStateWithLifecycle\|repeatOnLifecycle" anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt`
  — 002 landed iff `repeatOnLifecycle` is present and `collectAsStateWithLifecycle` is gone.)
- Does `anchor-compose/src/commonTest` exist (plan 001)?
- Has plan 007's `@InternalAnchorApi` landed (`grep -rn InternalAnchorApi anchor/src`)? — affects Q7.

**Verify**: record the three answers in your report.

### Step A3: Decision packet — MANDATORY STOP

Report to the maintainer, verbatim, the spec's §6 questions — at minimum:

1. **"Is multi-`HandleSignal` fan-out a contract or an accident?"** (spec recommends: contract → option (b′)).
2. Background delivery: held-and-delivered-on-return (default with 002) vs. dropped.
3. Held-signal expiry: none (bounded 64, drop-oldest) vs. TTL.
4. Raw `signals` collectors claim held signals (default) vs. only `HandleSignal`/`nativeSignals()`.
5. Public `ContainerViewModel.signalsMatching(accepts)` — acceptable, or gate behind plan 007's opt-in first?
6. Target release: 0.1.9 vs 0.2.0.

plus the A2 findings. **STOP. Do not start Phase B without an explicit GO naming
the option.** If the GO picks anything other than (b′), STOP — the spec's §3.1
sketch for that option needs its own plan.

## Phase B — Act (only after GO for option (b′))

### Step B1: Failing tests first

1. Add `anchor/src/commonTest/kotlin/dev/kioba/anchor/InitSignalDeliveryTest.kt` —
   the issue's test verbatim, plus a second case driving `ContainerViewModel`:

   ```kotlin
   @Test
   fun `signal posted from init reaches a collector attaching one frame later via ContainerViewModel`(): Unit =
     runBlocking {
       val runtime =
         AnchorRuntime<EmptyEffect, TestState, TestError>(
           initialState = { TestState(value = 0) },
           effectScope = { EmptyEffect },
           init = { post { Loaded } },
         )
       val vm = ContainerViewModel(runtime)
       delay(16)
       val received = withTimeoutOrNull(500) { vm.signals.first().provide() }
       assertEquals(Loaded, received)
     }
   ```

2. Add `anchor/src/commonTest/kotlin/dev/kioba/anchor/internal/SignalBusTest.kt`
   (package `dev.kioba.anchor.internal`) — port the seven cases from the spec's
   Appendix A "Bus ×7" (prototype `SignalBusPrototypeTest`), targeting the real
   `SignalBus` API from step B2:
   - `raw late collector receives held signal once and it is not replayed`
   - `typed collectors each receive their own held signal`
   - `unmatched typed signal stays held while another typed collector is live`
   - `live signal fans out to raw and typed collectors`
   - `held then live signals keep order`
   - `held buffer is bounded and drops oldest`
   - `concurrent posts vs subscribe lose nothing and duplicate nothing` (200 rounds × 200 posters on `Dispatchers.Default`, raw and typed)

   Use `CoroutineStart.UNDISPATCHED` for collectors that must be attached before a
   post, and `withTimeoutOrNull` for negative assertions (see spec Appendix B for
   the exact shapes).

**Verify**: `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.InitSignalDeliveryTest'`
→ both cases FAIL (dropped). `SignalBusTest` does not compile yet (no `SignalBus`) — expected.

### Step B2: Implement `SignalBus`

Create `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/SignalBus.kt`:

```kotlin
package dev.kioba.anchor.internal

import dev.kioba.anchor.Signal
import dev.kioba.anchor.SignalProvider
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Signal delivery with hold-until-accepted semantics.
 *
 * A posted signal goes live to every attached collector that accepts it. If none
 * does, it is held (at most [capacity], oldest dropped) and handed once to the
 * first accepting collector that attaches. Delivered signals are never replayed.
 *
 * Launches no coroutines of its own (Kotlin/Native: no unsupervised failures).
 */
internal class SignalBus(
  private val capacity: Int = HELD_CAPACITY,
) {
  private val lock = Mutex()
  private val held = ArrayDeque<SignalProvider>()
  private val typedAcceptors = mutableListOf<(Signal) -> Boolean>()
  private val raw = MutableSharedFlow<SignalProvider>(extraBufferCapacity = capacity)
  private val typed = MutableSharedFlow<SignalProvider>(extraBufferCapacity = capacity)

  suspend fun post(provider: SignalProvider) {
    val signal = provider.provide()
    lock.withLock {
      var delivered = false
      if (raw.subscriptionCount.value > 0) {
        raw.emit(provider)
        delivered = true
      }
      if (typedAcceptors.any { it(signal) }) {
        typed.emit(provider)
        delivered = true
      }
      if (!delivered) {
        if (held.size == capacity) held.removeFirst()
        held.addLast(provider)
      }
    }
  }

  /** Accept-all stream; the first collector drains everything held. */
  val signals: SharedFlow<SignalProvider> =
    raw.asSharedFlow().onSubscription {
      val drained = lock.withLock { held.toList().also { held.clear() } }
      drained.forEach { emit(it) }
    }

  /** Type-filtered stream; drains only held signals it [accepts]. */
  fun signalsMatching(accepts: (Signal) -> Boolean): Flow<SignalProvider> =
    flow {
      try {
        typed
          .onSubscription {
            val drained =
              lock.withLock {
                typedAcceptors += accepts
                val (match, rest) = held.partition { accepts(it.provide()) }
                held.clear()
                held.addAll(rest)
                match
              }
            drained.forEach { emit(it) }
          }.filter { accepts(it.provide()) }
          .collect { emit(it) }
      } finally {
        withContext(NonCancellable) { lock.withLock { typedAcceptors.remove(accepts) } }
      }
    }

  internal companion object {
    const val HELD_CAPACITY: Int = 64
  }
}
```

Why it is race-free is argued in spec §3.2 (relies on `onSubscription`'s documented
"*all emissions [...] immediately after this `onSubscription` action will be collected
by this subscription*" guarantee plus the single lock).

**Verify**: `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.internal.SignalBusTest'` → all 7 pass.
Re-run the stress case 3 times with `--rerun`; any failure is a STOP.

### Step B3: Wire `AnchorRuntime`

In `AnchorRuntime.kt`: delete `_signals` (lines 57-60) and its now-unused imports;
add `internal val signalBus: SignalBus = SignalBus()`; change

```kotlin
override val signals: SharedFlow<SignalProvider> = signalBus.signals

internal fun signalsMatching(accepts: (Signal) -> Boolean): Flow<SignalProvider> =
  signalBus.signalsMatching(accepts)

override suspend fun post(
  block: SignalScope.() -> Signal,
) {
  val signal = SignalScope.block()
  signalBus.post(SignalProvider { signal })
}
```

First `grep -rn "_signals" anchor anchor-compose anchor-test features` — any hit
outside `AnchorRuntime.kt` must be migrated (none at `0bc430c`).

**Verify**: `./gradlew :anchor:desktopTest` → exit 0; both `InitSignalDeliveryTest`
cases now pass; all pre-existing tests still pass.

### Step B4: Public typed accessor on `ContainerViewModel`

Add to `ContainerViewModel.kt` (public — `anchor-compose` can only reach public API):

```kotlin
/**
 * Signals accepted by [accepts]. Signals posted while no accepting collector was
 * attached are held and delivered once to the first accepting collector.
 * Used by `HandleSignal`; prefer that in Compose code.
 */
public fun signalsMatching(accepts: (Signal) -> Boolean): Flow<SignalProvider> =
  anchor.signalsMatching(accepts)
```

If the GO chose to gate it behind plan 007's opt-in and that annotation exists,
annotate it; if it doesn't exist yet, STOP (sequencing decision).

**Verify**: `./gradlew :anchor:desktopTest` → exit 0 (explicit-API mode accepts it).

### Step B5: Compose wiring (requires plan 002 merged)

1. In `LocalSignal.kt` replace the `LocalSignals` type:

   ```kotlin
   @PublishedApi
   internal fun interface SignalSource {
     fun signalsMatching(accepts: (Signal) -> Boolean): Flow<SignalProvider>
   }

   @PublishedApi
   internal val LocalSignals: ProvidableCompositionLocal<SignalSource> =
     staticCompositionLocalOf { SignalSource { emptyFlow() } }
   ```

2. In plan 002's `HandleSignal` loop, change only the collected flow:
   `signals.collect { ... }` → `source.signalsMatching { it is T }.collect { provider -> val signal = provider.provide(); if (signal is T) update.value(signal) }`
   where `val source = LocalSignals.current` and the `LaunchedEffect` key is `source`.
   Remove 002's KDoc "Known limitation: signals posted before the first composition
   collects [...] are not delivered" and replace it with the spec §3.2 contract.

3. In `RememberAnchor.kt` replace `signalFlow` with
   `val signalSource = remember(anchorScope) { SignalSource { accepts -> anchorScope.signalsMatching(accepts) } }`
   and `LocalSignals provides signalSource`.

4. Add to the plan-001/002 `HandleSignalTest.kt`:
   - `signals posted from init reach two typed handlers` — anchor with
     `init = { post { Nav.Home }; post { Toast.Show(1) } }`; content has
     `HandleSignal<Nav>` and `HandleSignal<Toast>`; `waitUntil` both received.
   - `signal posted while stopped is delivered on return` (only if the GO answered
     Q2 "deliver on return").

**Verify**: `./gradlew :anchor-compose:desktopTest` → exit 0 incl. the new tests.

### Step B6: iOS

No code change expected (`nativeSignals()` wraps `signals`, which keeps its type).
Update its KDoc with the contract (first `collect` receives signals held since
construction).

**Verify**: `./gradlew :anchor:compileKotlinIosSimulatorArm64` → exit 0 and
`./gradlew :anchor:iosSimulatorArm64Test` → exit 0 (runs `SignalBusTest` and
`InitSignalDeliveryTest` on Native; a signal-6 abort is a STOP).

### Step B7: Document the contract

- KDoc: `SignalAnchor.post` (`Anchor.kt:269-283`) and `AnchorSink.signals`
  (`Anchor.kt:85-88`) — paste the spec §3.2 contract.
- `docs/concepts.md` "Signals" section and `docs/compose.md` "Handling Signals"
  section: add a "Delivery guarantees" paragraph (contract + "held signals live in
  memory only — process death loses them; anything that must survive belongs in
  state").
- Run `bash scripts/generate-llms-full.sh`.

**Verify**: `git diff --stat docs/` shows `concepts.md`, `compose.md`, `llms-full.txt`.

### Step B8: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

Test-first order (B1 before B2):

1. **Regression (fails first)**: `InitSignalDeliveryTest` — issue reproducer
   verbatim + `ContainerViewModel` one-frame-late case. Pattern: the issue body;
   `CancellableBasicTest.kt` for style.
2. **Unit**: `SignalBusTest` — 7 cases (hold/drain once, typed routing, unmatched
   stays held, fan-out, order, bound, 200×200 concurrency stress). Pattern: spec
   Appendix B prototype tests.
3. **Compose (B5)**: two typed handlers receive init signals; optional background
   delivery. Pattern: plan-001 `RememberAnchorTest.kt` / plan-002 `HandleSignalTest.kt`.
4. **Native**: the same common tests via `:anchor:iosSimulatorArm64Test`.

Verification: `./gradlew :anchor:desktopTest`, `./gradlew :anchor-compose:desktopTest`,
`./gradlew :anchor:iosSimulatorArm64Test`, then `./gradlew build`.

## Done criteria

- [ ] Phase A decision packet delivered and an explicit GO naming option (b′) recorded
- [ ] `InitSignalDeliveryTest` (both cases) and `SignalBusTest` (7 cases) pass on desktop and iosSimulatorArm64
- [ ] Stress case passed 3 consecutive `--rerun`s
- [ ] `AnchorRuntime` no longer declares `_signals`; `AnchorSink.signals` still typed `SharedFlow<SignalProvider>`
- [ ] `HandleSignal` collects `signalsMatching { it is T }`; two-typed-handler init test passes
- [ ] KDoc (`post`, `AnchorSink.signals`, `HandleSignal`, `nativeSignals`) and docs state the contract; `docs/llms-full.txt` regenerated
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` row updated; plan 017 row marked superseded by this plan (if the coordinator/maintainer agrees)

## STOP conditions

Stop and report back if:

- **End of Phase A** (mandatory) — no Phase B without an explicit GO naming the option.
- The GO selects an option other than (b′) — this plan's Phase B does not apply.
- Step A1's reproducer passes on the current tree (delivery semantics already changed).
- Drift check shows `_signals`, `post`, `AnchorSink.signals` or `nativeSignals()` changed since `0bc430c`.
- Plan 002 has not landed when you reach Step B5 — ask whether to execute 002 first
  (preferred) or fold 002's `HandleSignal` rewrite into this plan. Steps B1-B4, B6-B8 may proceed.
- Any `SignalBusTest` case fails, or the stress case fails in any of 3 reruns.
- `:anchor:iosSimulatorArm64Test` aborts (signal 6) or fails only on Native.
- An existing test asserts that init-posted signals are NOT delivered.
- Making tests pass seems to require changing `ContainerViewModel.init` ordering
  (that is plan 004's territory).

## Maintenance notes

- Plan 002's KDoc "known limitation" line becomes false once this lands — B5
  removes it; if 002 lands *after* this plan, its executor must not re-add it.
- Plan 004 also edits `AnchorRuntime.kt` (subscribe) — trivial rebase; the two
  plans are independent. 004's reorder should also move `subscribe()` out of
  `init`'s `safeExecute` block to fix the spec §1.8 adjacent defect ("init raise
  skips `subscribe()`", confirmed by probe on `0bc430c`).
- `AnchorTestRuntime` (anchor-test) records `post` directly and never models
  delivery; plan 006's divergence KDoc should mention that "held until accepted"
  is production-only behavior.
- Reviewer focus: (1) the lock is held across `emit` when a live buffer is full —
  confirm no handler path can re-enter `SignalBus` while suspended; (2) typed
  acceptor removal happens in `finally` under `NonCancellable`; (3) raw collectors
  (e.g. analytics on `ContainerViewModel.signals`) claim held signals — documented
  edge (spec Q4).
- If held-signal expiry (spec Q3) is later wanted, add it inside `SignalBus.post`
  / drain (timestamp per entry) — no public API change.
- The parked branch `origin/fix-signal-handling-801299363541751028` implements a
  per-collector-dedup variant of replay (spec §3.1 (c)) that re-delivers after
  rotation — recommend deleting it (maintainer action; not part of this plan).
