# Plan 014: Migrate CancellableTest from real delays to virtual time

> **Triage note (2026-09-29, origin/master `0bc430c`)**: Complements plan 026 (`cancellable` key isolation, P2): 026's regression tests are the concurrency half, this plan is the virtual-time half.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableTest.kt anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableBasicTest.kt anchor/src/commonTest/kotlin/dev/kioba/anchor/JobIdentityTest.kt anchor/src/commonTest/kotlin/dev/kioba/anchor/JobIdentityEdgeCaseTest.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P3
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: tests
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

`CancellableTest` (and possibly its sibling job-identity tests) drives
`AnchorRuntime.cancellable` with `runBlocking` + real `delay()` calls. Real
sleeps make the suite slower than necessary and — worse — timing-dependent:
under CI load a 50ms window can stretch and flip outcomes. `kotlinx.coroutines.test`'s
`runTest` gives deterministic virtual time. The repo already depends on it
(`anchor-test` uses `runTest`; the test dependency is available to
`:anchor`'s commonTest via `libs.kotlin.coroutinesTest` — verify in step 1).

**Caveat that bounds this plan**: some cancellable tests intentionally
exercise *real parallelism* (mutex contention, concurrent `cancellable` calls
racing on `Dispatchers.Default`). Virtual time serializes those into
meaninglessness. Migrate per-test, not wholesale; keep genuinely-concurrent
tests on real dispatchers with explicit synchronization
(`CompletableDeferred` handshakes — several already exist), and document why.

## Current state

- `anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableTest.kt:1-15` imports:

  ```kotlin
  import kotlinx.coroutines.CompletableDeferred
  import kotlinx.coroutines.Dispatchers
  import kotlinx.coroutines.async
  import kotlinx.coroutines.awaitAll
  import kotlinx.coroutines.delay
  import kotlinx.coroutines.launch
  import kotlinx.coroutines.runBlocking
  ...
  ```

  Tests construct `AnchorRuntime(initialState = { TestState(value = 0) }, effectScope = { EmptyEffect }, ...)` directly.
- Sibling files likely sharing the pattern: `CancellableBasicTest.kt`,
  `JobIdentityTest.kt`, `JobIdentityEdgeCaseTest.kt` — audit each
  (`grep -ln "runBlocking" anchor/src/commonTest/kotlin/dev/kioba/anchor/*.kt`).
- `anchor/build.gradle.kts` commonTest deps currently:
  `implementation(libs.kotlin.test)` only — `coroutinesTest` may need adding
  (the alias exists; `anchor-test/build.gradle.kts:11` uses
  `libs.kotlin.coroutinesTest`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Fast suite | `./gradlew :anchor:desktopTest` | exit 0 |
| Timing report | `ls anchor/build/test-results/desktopTest/` → read suite `time=` attrs | reduced total time |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor/build.gradle.kts` (add `libs.kotlin.coroutinesTest` to commonTest if missing)
- `anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableTest.kt`
- `CancellableBasicTest.kt`, `JobIdentityTest.kt`, `JobIdentityEdgeCaseTest.kt`
  (same treatment where applicable)

**Out of scope**:
- `AnchorRuntime` production code — if a test cannot be made deterministic
  without changing the runtime, STOP.
- `anchor-test` module's own tests (already on `runTest`).

## Git workflow

- Branch: `test/virtual-time-cancellable`
- Commit style: gitmoji, e.g. `🧪 Migrate cancellable tests to virtual time`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Dependency + inventory

Add `implementation(libs.kotlin.coroutinesTest)` to `:anchor` commonTest deps
if absent. Inventory each test in the four files: classify as
(a) **time-sequenced** — uses `delay` purely to order/sequence events → migrate
to `runTest` + virtual delays; or (b) **truly concurrent** — meaningfully
exercises parallel execution/mutex contention → keep `runBlocking`, replace
bare `delay`-as-synchronization with `CompletableDeferred`/`Job.join`
handshakes, and add a comment `// real dispatcher intentionally: <reason>`.
Record the classification list in your report.

**Verify**: `./gradlew :anchor:desktopTest` → exit 0 (no behavior change yet).

### Step 2: Migrate class (a) tests

For each: `runBlocking` → `runTest`; remove timing slack (e.g.
`delay(100)`-to-be-safe becomes exact virtual delays); where the runtime
launches on `Dispatchers.Default` internally (e.g. `cancellable`'s `launch`),
note that `runTest`'s scheduler only virtualizes delays within its own
dispatcher — `AnchorRuntime.cancellable` launches in the *caller's* scope
(`coroutineScope { launch { ... } }`), so blocks run on the test dispatcher
and virtual time applies. If a specific test observes otherwise, classify it
(b) and move on.

**Verify after each file**: `./gradlew :anchor:desktopTest` → exit 0.

### Step 3: De-flake class (b) tests

No `delay(n)` may remain as a synchronization primitive in class-(b) tests —
each must use a deterministic handshake. `grep -n "delay(" <file>` and justify
every remaining occurrence in a comment.

**Verify**: `./gradlew :anchor:desktopTest` run 5× in a row
(`for i in 1 2 3 4 5; do ./gradlew :anchor:desktopTest --rerun-tasks -q || break; done`) → all green.

### Step 4: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

This plan modifies tests; the gate is: identical test count
(compare `anchor/build/test-results/desktopTest/` totals before/after), all
green, 5× stability run, reduced suite wall-time (report before/after).

## Done criteria

- [ ] No `runBlocking` remains except in justified class-(b) tests with comments
- [ ] No bare `delay()` used as synchronization in class-(b) tests
- [ ] Test count unchanged; 5× repeat run green
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- A migrated test reveals an actual production race (fails deterministically
  under virtual time) — that's a bug discovery, not a test problem; report it
  with the failing scenario.
- More than ~2 tests are unmigratable for reasons not covered by the (a)/(b)
  classification — the classification model is wrong; report.

## Maintenance notes

- New cancellable tests should default to `runTest`; reviewers should push
  back on new `runBlocking` + `delay` patterns unless justified as class (b).
- If plan 003/004 added subscription tests using real delays, apply the same
  classification there in a follow-up.
