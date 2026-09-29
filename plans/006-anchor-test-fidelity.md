# Plan 006: anchor-test fidelity — suspend effect blocks (#53) and documented runtime divergences

> **Triage note (2026-09-29, origin/master `0bc430c`)**: Steps 3–4: plan 028 (docs overhaul) writes the `docs/testing.md` half. Any wording here that tells consumers to test against `AnchorRuntime` must change, because it is `internal`.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor-test/src/commonMain/ docs/testing.md`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: tests
- **Planned at**: commit `492f7bc`, 2026-06-11
- **Issue**: https://github.com/kioba/anchor/issues/53 (part 1)

## Why this matters

`anchor-test` is a published product whose value is that consumer tests mean
what they say. Two gaps undercut that:

1. **#53**: `GivenScope.effect` takes a non-suspend block, so test setup cannot
   call suspending stubs.
2. **Undocumented divergence**: `AnchorTestRuntime` intentionally simplifies
   production semantics — `cancellable()` runs the block inline with **no
   cancellation of previous keyed jobs**, `effect()` ignores the requested
   `CoroutineContext`, and `post`/`emit` record without emitting to any flow.
   A consumer testing debounce/cancellation logic with `runAnchorTest` gets a
   green test against broken production behavior and is never told. The
   divergences are a legitimate design (deterministic recording), but they
   must be documented at the API and docs level, with guidance on what needs
   an integration test against the real runtime instead.

## Current state

- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/GivenScope.kt:14-16`:

  ```kotlin
  public suspend fun effect(
    f: R.() -> Unit,
  )
  ```

- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/GivenScopeImpl.kt:16-17,31-35`:

  ```kotlin
  @PublishedApi
  internal val effects: MutableList<(R.() -> Unit)> = mutableListOf()
  ...
  override suspend fun effect(
    f: R.() -> Unit,
  ) {
    effects.add(f)
  }
  ```

  `GivenScopeImpl` also implements `StepGivenScope` (sequence DSL) — check
  whether `StepGivenScope` declares `effect` too; if so it needs the same
  signature change.
- To find where the stored `effects` list is invoked, run:
  `grep -rn "\.effects" anchor-test/src/commonMain --include="*.kt"`.
  The invocation site(s) are inside the test scopes that run within `runTest`
  (see `AnchorTest.kt:14-32` — both `runAnchorTest` and `runAnchorSequenceTest`
  wrap everything in `kotlinx.coroutines.test.runTest`), so invoking suspend
  blocks there is straightforward.
- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorTestRuntime.kt:51-63`
  (the divergences to document):

  ```kotlin
  override suspend fun cancellable(
    key: Any,
    block: suspend Anchor<R, S, Err>.() -> Unit,
  ) {
    block()
  }

  override suspend fun <T> effect(
    coroutineContext: CoroutineContext,
    block: suspend R.() -> T,
  ): T =
    block(effectScope)
  ```

  Compare production `AnchorRuntime.cancellable` (`AnchorRuntime.kt:163-206`):
  cancels the previous job with the same key, mutex-guarded; and
  `AnchorRuntime.effect` (`AnchorRuntime.kt:135-141`): `withContext(coroutineContext)`.
- Docs page for the testing DSL: `docs/testing.md`. Any change to `docs/`
  requires regenerating `docs/llms-full.txt` via
  `bash scripts/generate-llms-full.sh` — CI fails otherwise
  (`.github/workflows/pr_check.yml`, job `llms_check`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Module tests | `./gradlew :anchor-test:desktopTest` | exit 0 |
| Dependents | `./gradlew :features:counter:desktopTest :features:config:desktopTest :features:main:desktopTest` | exit 0 |
| llms regen | `bash scripts/generate-llms-full.sh` | exit 0; `docs/llms-full.txt` updated |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/GivenScope.kt`
- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/GivenScopeImpl.kt`
- Whichever file(s) the `.effects` grep shows as invocation sites (signature
  ripple only)
- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorTestRuntime.kt`
  (KDoc only — no behavior change)
- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/AnchorTest.kt`
  (KDoc only)
- `anchor-test/src/commonTest/kotlin/dev/kioba/anchor/test/EffectTest.kt`
  (add a suspend-block test)
- `docs/testing.md`, `docs/llms-full.txt` (regenerated)

**Out of scope**:
- Making `AnchorTestRuntime.cancellable` actually cancel (behavioral redesign;
  deferred — see Maintenance notes).
- `anchor/` production sources.

## Git workflow

- Branch: `fix/anchor-test-fidelity`
- Commit style: gitmoji, e.g. `🐛 Make GivenScope.effect block suspendable` and
  `📝 Document AnchorTestRuntime divergences from production`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: #53 — suspend the effect block

- `GivenScope.kt`: `f: R.() -> Unit` → `f: suspend R.() -> Unit`.
- `GivenScopeImpl.kt`: `effects: MutableList<(suspend R.() -> Unit)>` and the
  override signature to match.
- Fix invocation sites found by the grep (they already run in suspend context
  under `runTest`).
- Check `StepGivenScope` for an `effect` declaration and align it.

**Verify**: `./gradlew :anchor-test:desktopTest` → exit 0 (source-compatible:
non-suspend lambdas coerce to suspend types).

### Step 2: Prove it with a test

In `EffectTest.kt`, add `effect block can call suspend functions`: a given
block whose `effect { }` calls a `suspend` function on the fake effect scope
(e.g. a suspending stub setter using `delay(1)` or a suspending fake). Assert
the test executes and the stubbed value is observed by the action under test.

**Verify**: `./gradlew :anchor-test:desktopTest` → exit 0, new test passes.

### Step 3: Document the test-runtime contract

- KDoc on `AnchorTestRuntime` (class level) and on its `cancellable`/`effect`
  overrides stating precisely: "records and executes inline; does NOT cancel a
  previous job with the same key / does NOT switch to the requested dispatcher;
  `post`/`emit` record actions without emitting to flows."
- KDoc on `runAnchorTest`/`runAnchorSequenceTest` (in `AnchorTest.kt`): one
  paragraph — "verifies action logic deterministically; cancellation,
  debouncing, dispatcher behavior, and subscription delivery are exercised
  only against the production runtime — write an integration test with
  `AnchorRuntime` for those (see `anchor/src/commonTest/.../CancellableTest.kt`
  for the pattern)."
- `docs/testing.md`: add a short section "What runAnchorTest does and does not
  verify" with the same content, matching the page's existing tone/structure.

**Verify**: `bash scripts/generate-llms-full.sh` → exit 0; then
`git status` shows `docs/llms-full.txt` modified (commit it).

### Step 4: Dependents and full build

**Verify**: `./gradlew :features:counter:desktopTest :features:config:desktopTest :features:main:desktopTest` → exit 0;
`./gradlew build` → exit 0.

## Test plan

- New: `effect block can call suspend functions` (`EffectTest.kt`), pattern:
  existing tests in the same file.
- Regression: full `:anchor-test` suite + the three feature modules' tests
  (they are the de-facto consumers of the DSL).

## Done criteria

- [ ] `GivenScope.effect` accepts `suspend R.() -> Unit`; all modules compile
- [ ] New suspend-block test passes
- [ ] KDoc divergence notes present on `AnchorTestRuntime`, `runAnchorTest`,
      `runAnchorSequenceTest`
- [ ] `docs/testing.md` has the new section AND `docs/llms-full.txt` is
      regenerated (CI `llms_check` would pass)
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- The `.effects` invocation site is NOT in a suspend context (would force API
  changes beyond this plan's scope).
- Any feature-module test breaks for a reason other than the changed signature.
- `StepGivenScope`'s structure makes the signature change ripple into the
  sequence DSL's public API beyond `effect` itself.

## Maintenance notes

- Deferred deliberately: making the test runtime's `cancellable` semantically
  faithful (keyed cancellation under virtual time). That requires a design
  decision — record-and-run-inline vs. real job management — and likely a new
  `assertCancelled(key)` verification API. Revisit if consumers report
  debounce-test confusion even with the new docs.
- Reviewer: check the KDoc wording does not promise future fidelity work.
- Step descriptions in the sequence DSL are documented as "reserved for future
  test reporting" (`docs/testing.md:262`) — a separate direction item builds
  on the recording this module already does.
