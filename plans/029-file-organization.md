# Plan 029: ABI-neutral file reorganization and a documented package map (#142 residual)

> **Executor instructions**: This is an INVESTIGATE-THEN-ACT plan with a hard
> decision gate. Complete Phase A (investigation). Write the findings into
> this file under "Investigation results". Then STOP for maintainer review
> before executing Phase B. Do not perform Phase B in the same run unless the
> operator explicitly pre-authorized it. When done (either phase), update the
> status row for this plan in `plans/README.md`. Skip that update if a reviewer
> dispatched you and told you they maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor/src anchor-compose/src anchor-test/src convention-plugins/ CLAUDE.md`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding. On a
> mismatch, treat it as a STOP condition. A known benign drift is plan 007
> having landed: `@OptIn(InternalAnchorApi::class)` lines in
> `internal/ContainedScope.kt` or `AnchorSequenceTestScope.kt`. Re-read those
> lines and continue.

## Status

- **Priority**: P3
- **Effort**: S (A: S, B: S)
- **Risk**: LOW. Every move is proven ABI-neutral by dump diff. The published
  modules are touched only by moving declarations within them, never by
  renaming a package.
- **Depends on**: none (hard). See "Sequencing" under Maintenance notes for 007, 013, 015, 016 and 020.
- **Category**: tech-debt
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: `plans/specs/029-file-organization.md`

## Why this matters

Issue #142 (2025-12) asked for "group by concept, not by type". Most of its
evidence is stale. `AnchorMarkers.kt` was merged into `Anchor.kt` and
`AnchorRuntime` moved to `internal/` in `1ee66f2` (#152). Compose and testing
became their own modules in `c08e12d`. The proposed `core/`/`runtime/`
package layout would rename the package of every public type, which breaks
every consumer at the source and binary level. That is rejected.

What remains is small but real:

- Three public types that consumers import by name live in files named after
  something else: `HandleSignal`, `AnchorStateScope` and `AnchorStepScope`.
- Runtime machinery (`AnchorRuntimeScope`) sits in a public-package file.
- The error capability is split: `DefectAnchor` is in `Anchor.kt` while
  `Raise` is in `Raise.kt`.
- `internal/` carries a dead `ContainedScope` with a KDoc that is false.
- Nothing documents which package holds what, or which file names cannot
  change without breaking consumers.

The last point matters most. A measured experiment (spec §1.3) showed that
renaming a Kotlin file that holds top-level functions silently changes the JVM
facade class (`LocalScopeKt` → `LocalAnchorKt`) and the Swift facade
(`RememberAnchorKt` → `IosAnchorKt`, which breaks
`iosApp/iosApp/ViewModelProtocol.swift:34`). The klib ABI dump does not catch
the Swift rename. A well-meaning future "tidy-up" of exactly the kind #142
proposes would ship a binary break unnoticed. This plan makes the safe subset
of #142 real and writes the rules down.

## Current state

No binary-compatibility tooling exists. There are no `.api` files, no
`apiCheck` task and no `abiValidation` block. Kotlin is `2.4.0`
(`gradle/libs.versions.toml:15`), and KGP 2.4.0 ships ABI validation built in
(`kotlin { abiValidation {} }` under `@ExperimentalAbiValidation`, tasks
`checkKotlinAbi`, `updateKotlinAbi` and `internalDumpKotlinAbi`;
`checkKotlinAbi` runs as part of `./gradlew build` once enabled).

Declarations to move (all line numbers at `0bc430c`):

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:91-108`:

  ```kotlin
  /**
   * Provides the ability to escalate a domain error to a defect.
   ...
   */
  @AnchorDsl
  public interface DefectAnchor<Err> where Err : Any {
    ...
    public fun orDie(error: Err): Nothing
  }
  ```

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/RememberAnchorScope.kt:3,46-66`:

  ```kotlin
  import dev.kioba.anchor.internal.AnchorRuntime
  ...
  /**
   * Default implementation of [RememberAnchorScope] that uses [AnchorRuntime].
   */
  internal object AnchorRuntimeScope : RememberAnchorScope {
    override fun <R : Effect, S : ViewState, Err : Any> create(
      ...
    ): Anchor<R, S, Err> =
      AnchorRuntime(
        ...
      )
  }
  ```

  Users: `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ViewModelProvider.kt:4`
  (`import dev.kioba.anchor.AnchorRuntimeScope`) and
  `anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt:33`, which uses the
  same package so no import is needed today.

- `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorSequenceTestScope.kt:55-95`:

  ```kotlin
  @AnchorTestDsl
  public class AnchorStepScope<R : Effect, S : ViewState, Err : Any> {
    ...
  }
  ```

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt:19-21,23-52`:

  ```kotlin
  @PublishedApi
  internal val LocalSignals: ProvidableCompositionLocal<Flow<SignalProvider>> =
    staticCompositionLocalOf { emptyFlow() }

  /**
   * Handles one-time [Signal]s emitted by an Anchor.
   ...
  public inline fun <reified T : Signal> HandleSignal(
  ```

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt:21-73`:
  `public interface AnchorStateScope<S : ViewState>` (line 28) and
  `@PublishedApi internal class AnchorStateScopeImpl` (line 59). `PreviewAnchor` (line 97)
  and `RememberAnchor` (line 150) stay.

- Dead code: `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ContainedScope.kt`
  (whole file) and `anchor/src/commonMain/kotlin/dev/kioba/anchor/AnchorScope.kt:3-4,41-62`:

  ```kotlin
  /**
   * Creates an AnchorScope from a ContainedScope.
   * ...
   * delegates to the provided ContainedScope. This is used internally by RememberAnchor
   * to provide safe action execution.
   ...
  @PublishedApi
  internal fun <R : Effect, S : ViewState> AnchorScope(
    containedScope: ContainedScope<out Anchor<R, S, *>, R, S, *>,
  ): AnchorScope<R, S> =
  ```

  `git grep -nE 'ContainedScope|AnchorScope\('` finds no reference outside
  these two files, either at HEAD or at any tag from `0.0.10` to `0.1.8`. The
  last caller was `0.0.9`. `LocalScope.kt:26`'s
  `AnchorScope<Effect, ViewState> { _ -> }` is the fun-interface SAM
  constructor, not this factory, and compiles without it (verified).

- `CLAUDE.md:281-315`, "Module Structure": a stale file tree. It has no
  packages, lists `ContainedScope.kt`, and omits the error-handling files and
  `AnchorConsumer.kt`. `CLAUDE.md` must keep the line ``Published version: `x.y.z` ``,
  because `.github/workflows/pr_check.yml:22` greps it.

Pinned file names. Do NOT rename these, and do NOT move their top-level
functions or properties out:

| File | Pinned facade | Surface |
|---|---|---|
| `anchor/.../Recover.kt` | `RecoverKt` | JVM + ObjC/Swift |
| `anchor/.../internal/ErrorHandling.kt` | `ErrorHandlingKt` | JVM + ObjC/Swift |
| `anchor/.../internal/NonFatal.kt` (common, android, desktop, ios) | `NonFatalKt` | JVM |
| `anchor/.../viewmodel/ViewModelProvider.kt` | `ViewModelProviderKt` | JVM + ObjC/Swift |
| `anchor/src/iosMain/.../RememberAnchor.kt` | `RememberAnchorKt` | ObjC/Swift (`iosApp/iosApp/ViewModelProtocol.swift:34`) |
| `anchor/.../AnchorScope.kt`, `internal/ContainedScope.kt` | `AnchorScopeKt`, `ContainedScopeKt` | JVM, until step B6 deletes them |
| `anchor-compose/.../AnchorAction.kt`, `AnchorConsumer.kt`, `LocalScope.kt`, `LocalSignal.kt`, `RememberAnchor.kt` | `<File>Kt` | JVM |
| `anchor-test/.../scopes/AnchorTestScope.kt` | `AnchorTestScopeKt` | JVM |

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Dump ABI (after enabling) | `./gradlew :anchor:updateKotlinAbi :anchor-compose:updateKotlinAbi :anchor-test:updateKotlinAbi` | writes `<module>/api/desktop/<module>.api` + `<module>/api/<module>.klib.api` |
| Check ABI vs dumps | `./gradlew :anchor:checkKotlinAbi :anchor-compose:checkKotlinAbi :anchor-test:checkKotlinAbi` | exit 0 (fails with `<<<ABI has changed>>>` on any diff) |
| ObjC header | `./gradlew :anchor:linkDebugFrameworkIosSimulatorArm64` | header at `anchor/build/bin/iosSimulatorArm64/debugFramework/anchor.framework/Headers/anchor.h` (macOS only) |
| Fast tests | `./gradlew :anchor:desktopTest :anchor-compose:desktopTest :anchor-test:desktopTest` | exit 0 (at `0bc430c`: 90 + 0 + 73 tests) |
| Sample consumers | `./gradlew :features:counter:iosSimulatorArm64Test :features:main:iosSimulatorArm64Test` | exit 0 (they import `AnchorStepScope`) |
| Full gate | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- Phase A: this plan file only, plus an uncommitted local edit to
  `convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts` and
  generated `*/api/` dumps. Both are reverted or kept per decision Q3.
- Phase B (post-GO):
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/{Anchor.kt,Raise.kt,RememberAnchorScope.kt,AnchorScope.kt}`
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntimeScope.kt` (create)
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ContainedScope.kt` (delete, if Q2 GO)
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ViewModelProvider.kt` (import only)
  - `anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt` (import only)
  - `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/{LocalSignal.kt,RememberAnchor.kt}`
  - `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/{HandleSignal.kt,AnchorStateScope.kt}` (create)
  - `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorSequenceTestScope.kt`
  - `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorStepScope.kt` (create)
  - `CLAUDE.md` ("Module Structure" section only)
  - If Q3 GO: `convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts` and
    `anchor/api/**`, `anchor-compose/api/**`, `anchor-test/api/**`

**Out of scope**:
- Any `package` directive change on a public or `@PublishedApi` declaration.
- Renaming any pinned file (table above), including `LocalScope.kt` and iosMain `RememberAnchor.kt`.
- Splitting the marker interfaces out of `Anchor.kt` (that recreates the pre-#152 `AnchorMarkers.kt`
  split #142 complained about; the markers' fate is plan 020's decision).
- `@InternalAnchorApi` gating (plan 007), user docs `docs/*.md` and `AGENTS.md` (#140, plans
  012 and 013), the `anchorS` rename (plan 016), and `features/*` (plan 015).
- Any change to a function body or KDoc beyond moving it. Deleting the false KDoc goes with B6.

## Git workflow

- Branch: `refactor/029-file-organization`
- Commits (gitmoji). Use one commit per concern so each is revertable:
  - (Q3 GO) `🏗️ Enable Kotlin ABI validation for published modules`: dumps generated from
    **unmodified** sources.
  - `♻️ Move declarations into files named after them (#142)`: B1–B5.
  - (Q2 GO) `🔥 Remove dead ContainedScope and AnchorScope factory`: B6.
  - `📝 Document package map and pinned file names (#142)`: B7.
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A: Investigation (always safe)

**A1: Drift check.** Run the drift-check command above. Then confirm the move
targets still exist exactly as excerpted:

```bash
grep -n "interface DefectAnchor" anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt
grep -n "object AnchorRuntimeScope" anchor/src/commonMain/kotlin/dev/kioba/anchor/RememberAnchorScope.kt
grep -n "class AnchorStepScope" anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorSequenceTestScope.kt
grep -n "fun <reified T : Signal> HandleSignal" anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt
grep -n "interface AnchorStateScope\|class AnchorStateScopeImpl" anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt
git grep -nE 'ContainedScope|[^.a-zA-Z]AnchorScope\(' -- '*.kt' ':!plans'
```

**Verify**: each grep prints exactly one declaration line. The last command
prints only lines inside `AnchorScope.kt` and `internal/ContainedScope.kt`.

**A2: Establish the ABI baseline on unmodified sources (the regression test,
written first).** In `convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts`
add the import `org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation`,
and inside `kotlin { }` directly after `explicitApi()` add:

```kotlin
  @OptIn(ExperimentalAbiValidation::class)
  abiValidation {}
```

This plugin is applied only by `anchor`, `anchor-compose` and `anchor-test`
(`grep -rn 'kmp-library' --include=build.gradle.kts .`). Then run the "Dump
ABI" command. Save copies of the baseline:
`mkdir -p /tmp/abi-142 && for m in anchor anchor-compose anchor-test; do cp -R $m/api /tmp/abi-142/$m; done`.
On macOS, also run the "ObjC header" command and
`cp anchor/build/bin/iosSimulatorArm64/debugFramework/anchor.framework/Headers/anchor.h /tmp/abi-142/anchor.h`.

**Verify**: the six dump files exist. At `0bc430c` the JVM dump has
`public final class dev/kioba/anchor/compose/LocalScopeKt` in
`anchor-compose/api/desktop/anchor-compose.api`, and the header contains
`swift_name("RememberAnchorKt")`. The "Check ABI vs dumps" command exits 0.

**A3: Prove the gate bites (negative control).** Temporarily run
`mv anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalScope.kt anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalAnchor.kt`.
Then run `./gradlew :anchor-compose:checkKotlinAbi`.

**Verify**: the task FAILS with `<<<ABI has changed>>>`, and
`./gradlew :anchor-compose:internalDumpKotlinAbi && diff /tmp/abi-142/anchor-compose/desktop/anchor-compose.api anchor-compose/build/kotlin/abi/desktop/anchor-compose.api`
shows `LocalScopeKt` → `LocalAnchorKt`. Move the file back. After that,
`checkKotlinAbi` exits 0 again. If the check does NOT fail, STOP: the gate is
not measuring the JVM facade.

**A4: Record and STOP.** Append an "## Investigation results" section to THIS
file. Include the A1 grep output summary, confirmation of the A2 baseline, the
A3 negative-control result, and a request for three decisions:

- **Q1**: GO on moves B1–B5 (ABI-neutral).
- **Q2**: GO on B6. This deletes dead `ContainedScope` and `AnchorScope()`. The
  ABI dump shows removal of three `@PublishedApi` symbols that no release since
  `0.0.9` references.
- **Q3**: Should ABI validation (B0) be committed permanently, or used only
  transiently for this change? Spec §6 Q2 has the trade-offs (committed
  `.api` files; ubuntu CI cannot compile iOS klibs, so it relies on
  `keepLocallyUnsupportedTargets`).

If Q3 is not yet answered, revert the convention-plugin edit and delete the
`*/api/` directories before stopping. Keep `/tmp/abi-142`. Set the
`plans/README.md` row to BLOCKED (awaiting maintainer decision). **STOP here.**

### Phase B: Execution (only after maintainer GO)

**B0 (only if Q3 GO)**: Re-apply the A2 convention-plugin edit on the branch,
run "Dump ABI" on the **unmodified** sources, and commit the edit plus the six
dump files as the first commit.

**Verify**: `./gradlew :anchor:checkKotlinAbi :anchor-compose:checkKotlinAbi :anchor-test:checkKotlinAbi`
exits 0 and `git status` is clean. If Q3 was NO-GO, re-apply the edit
**uncommitted** and restore the baseline as the reference dumps with
`for m in anchor anchor-compose anchor-test; do rm -rf $m/api; cp -R /tmp/abi-142/$m $m/api; done`.
B1–B6 can then still be checked with `checkKotlinAbi`. Revert both in B8.

**B1: `DefectAnchor` → `Raise.kt`.** Cut `Anchor.kt:91-108`, which is the
KDoc, `@AnchorDsl` and the interface, and paste it verbatim at the end of
`Raise.kt`. No import changes are needed, because both files are in package
`dev.kioba.anchor`.

**Verify**: "Check ABI vs dumps" exits 0 for `:anchor`.

**B2: `AnchorRuntimeScope` → `internal/AnchorRuntimeScope.kt`.** Cut
`RememberAnchorScope.kt:46-66` and remove its now-unused
`import dev.kioba.anchor.internal.AnchorRuntime` (line 3). Create
`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntimeScope.kt`
with `package dev.kioba.anchor.internal` and imports for
`dev.kioba.anchor.{Anchor, Effect, ErrorScope, RememberAnchorScope, SubscriptionsScope, ViewState}`,
followed by the cut block unchanged. Keep it `internal`, and do NOT add
`@PublishedApi`. In `viewmodel/ViewModelProvider.kt`, replace
`import dev.kioba.anchor.AnchorRuntimeScope` with
`import dev.kioba.anchor.internal.AnchorRuntimeScope`. In iosMain
`RememberAnchor.kt`, add `import dev.kioba.anchor.internal.AnchorRuntimeScope`.

**Verify**: "Check ABI vs dumps" exits 0 for `:anchor`, and
`./gradlew :anchor:compileKotlinIosSimulatorArm64` exits 0.

**B3: `AnchorStepScope` → `scopes/AnchorStepScope.kt`.** Cut
`AnchorSequenceTestScope.kt:55-95`, which is `@AnchorTestDsl` through the
class's closing brace, plus the blank line after it. Create
`anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorStepScope.kt`
with `package dev.kioba.anchor.test.scopes` and imports
`dev.kioba.anchor.{Anchor, Effect, ViewState}` and `dev.kioba.anchor.test.AnchorTestDsl`.
`SequenceStep`, `GivenScopeImpl`, `StepGivenScope`, `VerifyScope` and
`VerifyScopeImpl` are all in the same package, so they need no import.

**Verify**: "Check ABI vs dumps" exits 0 for `:anchor-test`, and the "Sample
consumers" command exits 0.

**B4: `HandleSignal` → `compose/HandleSignal.kt`.** Cut `LocalSignal.kt:23-52`,
which is the KDoc through the end of `HandleSignal`. Leave `LocalSignals` where
it is. Its JVM facade `LocalSignalKt.getLocalSignals` is pinned, because
0.1.8 consumers' inlined `RememberAnchor`/`HandleSignal` call it. Create
`HandleSignal.kt` in package `dev.kioba.anchor.compose` with the imports
`androidx.compose.runtime.{Composable, DisallowComposableCalls, LaunchedEffect, getValue, rememberUpdatedState}`,
`androidx.lifecycle.compose.collectAsStateWithLifecycle` and
`dev.kioba.anchor.Signal`. Trim `LocalSignal.kt`'s imports to
`ProvidableCompositionLocal`, `staticCompositionLocalOf`, `SignalProvider`,
`Flow` and `emptyFlow`.

**Verify**: "Check ABI vs dumps" exits 0 for `:anchor-compose`. This works
because `HandleSignal` is a reified inline function, which the JVM backend
emits as `ACC_SYNTHETIC` and which is absent from the dump.

**B5: `AnchorStateScope` and `AnchorStateScopeImpl` → `compose/AnchorStateScope.kt`.**
Cut `RememberAnchor.kt:21-73`, from the KDoc of `AnchorStateScope` through the
closing brace of `AnchorStateScopeImpl`, plus the blank line after it. Create
`AnchorStateScope.kt` in package `dev.kioba.anchor.compose` with the imports
`androidx.compose.runtime.{Composable, getValue, remember, rememberUpdatedState}`,
`androidx.lifecycle.compose.collectAsStateWithLifecycle`,
`dev.kioba.anchor.ViewState`, `kotlinx.coroutines.Dispatchers` and
`kotlinx.coroutines.flow.StateFlow`. Remove the imports from `RememberAnchor.kt`
that became unused: check each of `getValue`, `rememberUpdatedState`,
`collectAsStateWithLifecycle`, `Dispatchers` and `StateFlow` against the
remaining body. `PreviewAnchor` still uses `MutableStateFlow`, and
`RememberAnchor` uses `remember`.

**Verify**: "Check ABI vs dumps" exits 0 for `:anchor-compose`, and "Fast
tests" exits 0.

**B6 (only if Q2 GO): delete dead code.** Delete
`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ContainedScope.kt`. In
`AnchorScope.kt`, delete lines 41-62 (the factory and its KDoc) and the imports
at lines 3-4. The file then holds only `public fun interface AnchorScope`.
Then run the "Dump ABI" command for `:anchor`.

**Verify**: `diff /tmp/abi-142/anchor/desktop/anchor.api anchor/api/desktop/anchor.api`
shows ONLY the removal of `dev/kioba/anchor/AnchorScopeKt`,
`dev/kioba/anchor/internal/ContainedScope` and
`dev/kioba/anchor/internal/ContainedScopeKt`. The klib diff shows ONLY the
matching `ContainedScope` interface, `execute` and `AnchorScope(...)` lines.
`./gradlew :anchor-compose:desktopTest` exits 0. This proves that
`LocalScope.kt`'s `AnchorScope<Effect, ViewState> { }` resolves to the SAM
constructor. If Q3 was GO, commit the updated `anchor/api/*` in this commit.

**B7: Package map in `CLAUDE.md`.** Replace the tree under "## Module Structure"
(`CLAUDE.md:281-315`) with the following:

1. A table with columns module / package / holds / "put new code here when…":
   - `anchor`: `dev.kioba.anchor` (public core API: `Anchor.kt` holds the markers
     and capability interfaces; `Raise.kt`, `Recover.kt` and
     `AnchorExceptions.kt` hold error handling; `ErrorScope.kt`,
     `BaseAnchorScope.kt`, `PureAnchor.kt`, `AnchorScope.kt`,
     `RememberAnchorScope.kt` and `SubscriptionDsl.kt`; iosMain
     `RememberAnchor.kt` and `NativeFlows.kt`),
     `dev.kioba.anchor.internal` (runtime machinery: `AnchorRuntime`,
     `AnchorRuntimeScope`, `RaiseScope`, `NonFatal`, and `ErrorHandling`,
     which is public for `anchor-test`; see plan 007),
     `dev.kioba.anchor.viewmodel` (ViewModel bridge).
   - `anchor-compose`: `dev.kioba.anchor.compose`.
   - `anchor-test`: `dev.kioba.anchor.test` (entry points `runAnchorTest` and
     `runAnchorSequenceTest`) and `dev.kioba.anchor.test.scopes` (DSL scopes
     and the recording runtime).
2. The rule set from spec §1.3 in 4 bullets: a public package change breaks
   consumers; class-only moves are free; top-level fun/val file moves break the
   JVM and Swift facades; plain `internal` is free.
3. The "Pinned file names" table from this plan's Current state, minus the
   `AnchorScope.kt`/`ContainedScope.kt` row if B6 ran.

Do not touch the ``Published version: `…` `` line.

**Verify**: `grep -c 'dev.kioba.anchor.test.scopes' CLAUDE.md` ≥ 1.
`grep -n 'ContainedScope' CLAUDE.md` has no output (if B6 ran). The CI version
check still matches:
`grep -oE 'Published version: \`[0-9]+\.[0-9]+\.[0-9]+' CLAUDE.md` prints one line.

**B8: Full gate and header check.** If Q3 was NO-GO, revert the convention-plugin
edit and delete `*/api/` now. Run `./gradlew build`. On macOS, run the "ObjC
header" command, then
`diff /tmp/abi-142/anchor.h anchor/build/bin/iosSimulatorArm64/debugFramework/anchor.framework/Headers/anchor.h`.

**Verify**: `./gradlew build` exits 0, and the header diff is empty.

## Test plan

This is a no-behavior refactor. The regression test is the ABI gate, and it is
established before any move:

1. **First (A2)**: baseline JVM dumps, klib dumps and the ObjC header from
   unmodified `0bc430c` sources. `checkKotlinAbi` passes.
2. **Negative control (A3)**: a rename of a pinned file (`LocalScope.kt`) makes
   `checkKotlinAbi` FAIL. This proves the gate detects exactly the class of
   break this plan avoids.
3. **Per step (B1–B5)**: `checkKotlinAbi` passes. The dumps are byte-identical.
4. **B6**: the dump diff equals exactly the three expected removals.
5. **Behavior**: existing suites stay green. At `0bc430c`, `:anchor:desktopTest`
   runs 90 tests and `:anchor-test:desktopTest` runs 73. The sample sequence
   tests (`CounterSequenceTest`, `MainSequenceTest`) import `AnchorStepScope`
   and must compile unchanged.
6. **Swift surface**: the ObjC header diff is empty (B8). This matters because
   the klib dump does not see Swift facade names.

Pre-validation at planning time: in a disposable worktree at `0bc430c`, B1–B6
were applied exactly as written. The JVM dump, klib dump and ObjC header showed
zero diff for B1–B5, and the three expected removals for B6. "Fast tests" and
"Sample consumers" exited 0. See the spec §1.3 and the note below for the full
`./gradlew build` result.

## Done criteria

Phase A:
- [ ] "Investigation results" section appended, with the A3 negative-control result and Q1–Q3
- [ ] `plans/README.md` row set to BLOCKED (awaiting maintainer decision)

Phase B (post-GO):
- [ ] `HandleSignal.kt`, `AnchorStateScope.kt`, `scopes/AnchorStepScope.kt` and `internal/AnchorRuntimeScope.kt` exist. `grep -n "interface DefectAnchor" anchor/src/commonMain/kotlin/dev/kioba/anchor/Raise.kt` matches
- [ ] JVM and klib dumps are identical to the A2 baseline, except for the B6 removals if Q2 was GO
- [ ] ObjC header diff is empty
- [ ] `CLAUDE.md` "Module Structure" contains the package map and the pinned-file table. The version line is intact
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- (Built into the plan: the end of Phase A is a mandatory stop.)
- A3's negative control does not make `checkKotlinAbi` fail.
- Any of B1–B5 produces a non-empty dump or header diff. Do not "fix" it by
  running `updateKotlinAbi`. The move is then not ABI-neutral, and the analysis
  in spec §1.3 has drifted.
- B6's diff contains anything beyond the three expected removals, or a new
  caller of `ContainedScope`/`AnchorScope(` appears (A1's last grep).
- A move appears to require changing a `package` directive of a public or
  `@PublishedApi` declaration, or renaming a pinned file.
- `abiValidation {}` does not resolve with the Kotlin version on HEAD (for
  example, if Kotlin was downgraded below 2.4.0, where the DSL shape differs).
  Report the version instead of improvising another validator.

## Maintenance notes

- **Sequencing**:
  - **Plan 007** (`@InternalAnchorApi`): order-independent. If 007 lands first,
    B6 deletes a file 007 annotated (`ContainedScope.kt`'s `safeExecute` call
    site). If this plan lands first, 007's call-site list shrinks by one. If
    B0 is committed, 007 must run `updateKotlinAbi`, because the annotation
    class is new ABI. It should also resolve its own maintenance note on
    whether to `filters { exclude { annotatedWith.add("dev.kioba.anchor.InternalAnchorApi") } }`.
    Add `InternalAnchorApi.kt` to the package map.
  - **Plan 016** (`anchorS` rename, still TODO): the klib dump header reads
    `// Library unique name: <anchorS:anchor>`, and the built klib manifest has
    `unique_name=anchorS\:anchor`. The root-project name therefore leaks into
    published klib metadata. That is an input for 016's A5. If B0 is
    committed first, 016-B2 will show up as a klib-dump header diff. Run
    `updateKotlinAbi` there deliberately.
  - **Plan 015** (feature convention): no file overlap. 015 creates
    `dev.kioba.feature-library` and leaves `dev.kioba.kmp-library` alone. If a
    future change makes sample features apply `kmp-library`, move
    `abiValidation {}` so that unpublished modules do not get `.api` files.
  - **Plan 013** (Dokka): the B7 package map can be mirrored as Dokka
    `# Package dev.kioba.anchor…` module-doc sections. 013's Step 3 already
    handles hiding `dev.kioba.anchor.internal`.
  - **Plan 020** (marker interfaces): this plan deliberately leaves the markers
    in `Anchor.kt`. If 020 decides to remove or enrich them, update the package
    map in the same PR.
  - **#140 / plan 012**: user-facing docs are theirs. This plan edits only
    contributor-facing `CLAUDE.md`.
- **Reviewer focus**: B2 is the only package change, and it applies to a plain
  `internal` object. Confirm that it has no `@PublishedApi`. Confirm that
  `LocalSignals` and `PreviewAnchor` did NOT move.
- **Tracker**: after Phase B, #142 can be closed with a link to the spec. The
  original claims are resolved (by #152/`c08e12d`) or rejected as breaking
  (`core/` packages). The maintainer does this.
- Future renames of pinned files should be batched into a release that already
  breaks the binary API (spec §6 Q3), with a release-note line.

## Investigation notes (planning-time pre-validation, 2026-09-29)

Worktree at `0bc430c` with the A2 edit plus B1–B6 applied:
`:anchor/:anchor-compose/:anchor-test:checkKotlinAbi` passed against the baseline for B1–B5.
`desktopTest` passed for `anchor` (90) and `anchor-test` (73).
`:features:counter` and `:features:main` `iosSimulatorArm64Test` passed. The ObjC header
showed 0 differing lines after B1–B6. B6 does not touch the header, because
`@PublishedApi` symbols are not ObjC-exported.

The first `./gradlew build --continue` run failed only
`:anchor-test:testAndroidHostTest` and `:androidApp:mergeDebugAssets`. Both
reported "Timeout waiting to lock journal cache / file hash cache … in use by
another process". That was Gradle-home lock contention from a parallel build,
not a code failure. The anchor-test Android host results showed 12 suites with
`failures="0" errors="0"`. After rerunning those tasks (BUILD SUCCESSFUL),
`./gradlew build` exited 0 (640 tasks). This run included `checkKotlinAbi` for
all three modules, because KGP wires it into `check`.
