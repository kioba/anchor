# Plan 008: Remove the never-compiled counter test file and port its cases to commonTest

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- features/counter/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

`features/counter/src/test/kotlin/dev/kioba/anchor/features/config/CounterAnchorTest.kt`
is dead weight that *looks* like coverage:

- It declares package `dev.kioba.anchor.features.config` and imports
  `dev.kioba.anchor.features.config.data.CounterAnchor`, `CounterState`,
  `counterAnchor`, `increment`, `decrement` and
  `...config.model.CounterSignal` — **none of which exist** (the config
  module's `data/` contains only `ConfigAnchor.kt` and `ConfigEffects.kt`).
- CI (`./gradlew build`) is green, which proves the file is never compiled:
  `src/test/` is not a registered source set in this Kotlin Multiplatform
  module (KMP host tests live in `commonTest`, wired via the
  `android-multiplatformLibrary` plugin's host-test setup).

Its two BDD test cases (increment/decrement with signal assertions via
`runAnchorTest`) are worth having — the counter feature currently has only a
sequence test. Port them against the *counter* module's real symbols, then
delete the dead file.

## Current state

- Dead file: `features/counter/src/test/kotlin/dev/kioba/anchor/features/config/CounterAnchorTest.kt`
  — two tests using `runAnchorTest(RememberAnchorScope::counterAnchor)` with
  `given/on/verify`, `assertState`, `assertSignal { CounterSignal.Increment }`.
- Live counter test (the exemplar for placement, package, and imports):
  `features/counter/src/commonTest/kotlin/dev/kioba/anchor/features/counter/CounterSequenceTest.kt`.
- Counter module sources: `features/counter/src/commonMain/kotlin/dev/kioba/anchor/features/counter/`
  — check `data/` for the actual `counterAnchor` factory, action extensions
  (`increment`, `decrement`), state class, and the signal type's real name and
  package before porting.
- `features/counter/build.gradle.kts` declares targets via
  `android.multiplatformLibrary` + `kotlinMultiplatform` plugins; no `src/test`
  wiring exists (confirm with step 1).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Module tests | `./gradlew :features:counter:desktopTest` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- Delete `features/counter/src/test/` (entire directory).
- Create `features/counter/src/commonTest/kotlin/dev/kioba/anchor/features/counter/CounterAnchorTest.kt`.

**Out of scope**:
- `features/config/` — nothing there is wrong.
- The counter module's main sources and build file.
- Other feature modules' coverage (separate concern).

## Git workflow

- Branch: `chore/remove-dead-counter-test`
- Commit style: gitmoji, e.g. `🔥 Remove never-compiled counter test, port cases to commonTest`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Prove the file is dead (sanity check)

Run `./gradlew :features:counter:build` — exit 0 *with the broken imports
still present* confirms `src/test` is not compiled. If this FAILS citing
`CounterAnchorTest.kt`, STOP: the source set became wired since planning and
the situation changed.

**Verify**: command exits 0.

### Step 2: Port the two test cases

Read the counter module's actual symbols
(`features/counter/src/commonMain/.../data/`, `.../model/` if present) and the
`CounterSequenceTest.kt` imports. Write
`features/counter/src/commonTest/.../CounterAnchorTest.kt` with the dead
file's two cases (`counter increment updates state`,
`counter decrement updates state`) adapted to the real types — same
`runAnchorTest` + `given`/`on`/`verify` structure, including the
`assertSignal` lines if the counter feature posts signals on
increment/decrement (check the action implementations; if they post no
signals, drop the `assertSignal` lines and note it in your report).

**Verify**: `./gradlew :features:counter:desktopTest` → exit 0, 2 new tests
listed as passing (check `features/counter/build/test-results/desktopTest/`).

### Step 3: Delete the dead directory

`git rm -r features/counter/src/test`

**Verify**: `./gradlew build` → exit 0.

## Test plan

The ported tests ARE the plan. Pattern: `CounterSequenceTest.kt` (same
directory). Verification in steps 2-3.

## Done criteria

- [ ] `features/counter/src/test/` no longer exists
- [ ] `CounterAnchorTest.kt` exists in commonTest with 2 passing tests
- [ ] `./gradlew :features:counter:desktopTest` and `./gradlew build` exit 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- Step 1 fails (the file IS compiled — drift).
- The counter module has no signal type / the actions' shape makes the ported
  assertions meaningless — report what the actions actually do instead of
  inventing assertions.

## Maintenance notes

- Root cause to keep in mind for review: `src/test/` silently ignored is a
  KMP foot-gun; if it recurs, a CI guard (`! find . -path '*/src/test/kotlin' -not -path '*/build/*' | grep -q .` for KMP modules) could be added —
  deferred, not part of this plan.
