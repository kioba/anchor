# Plan 017: Design note — producer-side signal delivery semantics (and the fate of the parked January refactor)

> **Triage note (2026-09-29, origin/master `0bc430c`)**: SUPERSEDED by spec/plan 024 (#266), which answers all four sections of this note with prototypes: the §1 truth table, the §2 option comparison, the §3 parked-branch verdict (delete), and the §4 recommendation, option (b′) type-aware buffering. Do not execute.

> **Executor instructions**: This is a DESIGN/SPIKE plan. The deliverable is a
> design note at `plans/design/signal-delivery-semantics.md` plus a small
> throwaway prototype — NOT a production code change. Do not modify any file
> outside `plans/`. Follow the investigation steps, answer every open
> question with evidence, and STOP at the decision gate. When done, update
> the status row in `plans/README.md`.
>
> **Drift check (run first)**: `git fetch origin --quiet; git diff --stat origin/master -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt`
> Work against **origin/master**, not the local branch — this plan was written
> when the local checkout (492f7bc) was 7 commits behind (tip ec1017c).

## Status

- **Priority**: P2
- **Effort**: M (investigation + note + prototype; no production change)
- **Risk**: LOW (read-only spike)
- **Depends on**: complements plans/002-handlesignal-lossless-delivery.md (consumer side); neither blocks the other
- **Category**: direction
- **Planned at**: commit `492f7bc` (origin/master `ec1017c`), 2026-06-11

## Why this matters

Signals are Anchor's one-shot UI events. Their delivery guarantee has never
been *chosen* — it has been patched:

- Issue **#144** ("replay=0 causing signal loss", closed COMPLETED 2026-01-23
  with no closing comment) was resolved by commit `1ee66f2` adding
  `extraBufferCapacity = 64` — a buffer against slow collectors, **not** a fix
  for the reported race: with `replay = 0`, a signal posted when *no collector
  exists yet* (app startup, deep link, `init` block) is dropped by SharedFlow
  contract regardless of buffer size.
- A 33-file refactor branch from the same date
  (`origin/fix-signal-handling-801299363541751028`, 2 commits, 2026-01-23,
  no PR) restructures signal handling broadly and was abandoned; master
  diverged significantly since (error-handling Err machinery, sequence DSL).
- Plan 002 fixes the *consumer* side (Compose conflation). It deliberately
  leaves the producer-side guarantee out of scope.

Before 1.0, "what happens to a signal posted before anyone is listening, or
while the app is backgrounded" must be a documented decision, not an accident
of SharedFlow defaults.

## Current state

- Producer — `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:56-59,208-213`:

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

- Consumers: Compose `HandleSignal`
  (`anchor-compose/.../LocalSignal.kt` — post-plan-002 it collects the stream
  directly; pre-002 it conflates), and iOS
  `NativeSharedFlow.collect` (`anchor/src/iosMain/kotlin/dev/kioba/anchor/NativeFlows.kt`)
  plus the Swift sample's `@Published var signal` in
  `iosApp/iosApp/ViewModelProtocol.swift` (conflates to latest by design of
  `@Published`).
- Multiple simultaneous collectors are legitimate (several `HandleSignal`
  blocks per screen) — fan-out semantics today: every collector sees every
  signal emitted *while it is subscribed*.
- The parked branch: inspect with
  `git log origin/fix-signal-handling-801299363541751028 --oneline`,
  `git diff --stat $(git merge-base origin/master origin/fix-signal-handling-801299363541751028)..origin/fix-signal-handling-801299363541751028`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Branch archaeology | the two git commands above | inventory for §3 of the note |
| Issue history | `gh issue view 144 --repo kioba/anchor --comments` | closure context |
| Prototype tests | `./gradlew :anchor:desktopTest --tests "*SignalSemantics*"` (in a scratch worktree only, if you prototype as a test) | exit 0 |

## Scope

**In scope** (writable):
- `plans/design/signal-delivery-semantics.md` (create — the deliverable)
- `plans/README.md` (status row)
- A scratch git worktree if you prototype (never the user's tree)

**Out of scope**:
- ANY production source change. Plan 002 owns the consumer fix; a future plan
  (written after this note is accepted) owns the producer change.
- Deleting or merging the parked branch (recommendation only — maintainer acts).

## Steps

### Step 1: Characterize today's guarantees precisely

Write (or reason through, citing kotlinx.coroutines documented SharedFlow
semantics) the truth table for: signal posted (a) before any collector,
(b) with one slow collector and a 65-signal burst, (c) with two collectors
where one is slow, (d) while Android lifecycle is below STARTED (post-plan-002
`repeatOnLifecycle` window). Each row: delivered / dropped / delivered-late,
to whom. This table is §1 of the note.

**Verify**: every row cites either a doc excerpt or a prototype observation —
no "probably".

### Step 2: Enumerate and evaluate the candidate designs

At minimum these four, each with: delivery guarantee, fan-out behavior,
`init`-posted-signal behavior, memory behavior, migration cost from today:

1. **Status quo, documented** — SharedFlow(buffer=64); pre-subscription
   signals drop; write it into KDoc/docs and close the question.
2. **Bounded replay + collector-side dedup** — replay=N re-delivers to every
   new collector; requires consume-once bookkeeping (signal IDs) to avoid
   re-showing toasts on recomposition/navigation return. Note interaction
   with plan 002's `repeatOnLifecycle` restart (re-collection = re-delivery).
3. **Consume-once queue (Channel-backed)** — guarantees delivery to exactly
   one consumer; breaks multi-`HandleSignal` fan-out (today's documented
   pattern — see `HandleSignal` KDoc usage). Would need either per-type
   routing or a documented single-consumer rule.
4. **Buffer-until-first-subscriber** — custom: queue signals while
   `subscriptionCount == 0`, flush on first collector, SharedFlow semantics
   after. Solves the startup race specifically without changing steady-state
   fan-out. (Look at `_signals.subscriptionCount` — same mechanism plan 004
   uses for events.)

**Verify**: each option's row in the comparison table in §2 of the note has a
concrete code sketch (10-20 lines max each).

### Step 3: Decide the parked branch's fate

From the step-1 archaeology: does
`origin/fix-signal-handling-801299363541751028` implement any of the §2
options? Is anything in it worth cherry-picking? Recommendation: resurrect /
mine-for-parts / delete, with one paragraph of reasoning. This is §3.

### Step 4: Recommend

§4: one recommended option with rationale against the table, the migration
sketch (which files change, breaking or not), and explicitly listed open
questions only the maintainer can answer (e.g. "is multi-HandleSignal
fan-out a contract or an accident?"). **STOP here** — the recommendation gates
any implementation plan.

## Test plan

If you prototype option 4 (recommended to de-risk it), do it as a scratch
test against `AnchorRuntime` in a disposable worktree; include the test code
verbatim in the note's appendix. No prototype lands in the repo.

## Done criteria

- [ ] `plans/design/signal-delivery-semantics.md` exists with §1 truth table,
      §2 four-option comparison (each with code sketch), §3 branch verdict,
      §4 recommendation + open questions
- [ ] No file outside `plans/` modified (`git status`)
- [ ] `plans/README.md` row updated to DONE (design note delivered) or BLOCKED

## STOP conditions

- The end of Step 4 is a mandatory stop — no implementation.
- If origin/master's `AnchorRuntime._signals` changed since planning (someone
  picked a semantics already), reconcile: the note becomes a review of that
  choice instead.

## Maintenance notes

- Whichever option is chosen must be re-checked against plan 002's consumer
  loop (back-pressure interaction) and against iOS consumers
  (`NativeSharedFlow`, `@Published` conflation in the Swift sample — plan 019
  territory).
- The chosen guarantee belongs in docs (#140's threading/guarantees section)
  verbatim from this note's §1 table.
