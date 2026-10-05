# Spec: #142 — File organization (ABI-neutral residual cleanup + package map)

- **Issue**: [#142](https://github.com/kioba/anchor/issues/142), "[Code Organization] File
  organization is confusing - group by concept not by type" (opened 2025-12-26 by the staff-engineer
  review, child of #145, labelled `medium-priority`)
- **Verdict**: PARTIALLY CONFIRMED. The layout the issue describes no longer exists. A smaller
  residual confusion remains.
- **Severity**: P3 (internal cleanup and organization; nothing user-visible breaks today)
- **Verified at**: `0bc430c` (origin/master), 2026-09-29
- **Plan**: `plans/029-file-organization.md`

## 1. Problem statement with evidence

### 1.1 Claim-by-claim check against `0bc430c`

When the issue was filed (tree at `fbf9d4f`, 2025-12-26), `anchor/` had `Anchor.kt`,
`AnchorMarkers.kt`, a root-package `AnchorRuntime.kt`, and the Compose code under
`anchor/src/androidMain/.../compose/`. Two commits have since changed that layout:

- `1ee66f2` (#152, 2026-01-23) deleted `AnchorMarkers.kt`, merged the markers into `Anchor.kt`, and
  moved `AnchorRuntime.kt` into `internal/`.
- `c08e12d` (2026-04-08) extracted the Compose code into the `anchor-compose` module.

| # | Issue claim | Status at `0bc430c` | Evidence |
|---|-------------|---------------------|----------|
| 1 | Interfaces are split between `Anchor.kt` (5) and `AnchorMarkers.kt` (4) | **ALREADY FIXED** | `AnchorMarkers.kt` is gone. The markers now live at `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:15-66` |
| 2 | "State, Signal, and Event are in different files" | **ALREADY FIXED**, and now the reverse is true | `Anchor.kt` (283 lines) holds all four markers, their defaults, `SignalProvider`, and every capability interface, 20 top-level declarations in all |
| 3 | "File names don't describe content" | **PARTIALLY TRUE, but for different files** | See §1.2 |
| 4 | Unclear boundary between `Anchor.kt` and `AnchorMarkers.kt` | **ALREADY FIXED** | Same evidence as #1 |
| 5 | Proposed `core/ runtime/ compose/ testing/` | `compose/` and `testing/` **already done as modules**. `runtime/` roughly matches the existing `internal` and `viewmodel` packages. `core/` would be a **breaking public package move** (§1.3) | `settings.gradle.kts`: `anchor-compose` (`dev.kioba.anchor.compose`) and `anchor-test` (`dev.kioba.anchor.test[.scopes]`) |
| 6 | Alternative: "keep flat but rename" | **Adoptable only for class-only moves.** Renaming a file that holds top-level functions breaks binaries (§1.3) | Experiments E2–E4 |

### 1.2 Residual confusion that is real today

| File (at `0bc430c`) | What it actually holds | Why that is confusing |
|---|---|---|
| `anchor-compose/.../compose/LocalSignal.kt:41` | the public `HandleSignal` composable, plus the `@PublishedApi` `LocalSignals` | A user looking for `HandleSignal` finds no `HandleSignal.kt` |
| `anchor-compose/.../compose/RememberAnchor.kt:28,59` | the public `AnchorStateScope` and `AnchorStateScopeImpl`, alongside `PreviewAnchor` and `RememberAnchor` | Sample code imports `dev.kioba.anchor.compose.AnchorStateScope` (`features/main/src/androidMain/.../MainUi.kt:16`), but no file carries that name |
| `anchor-test/.../scopes/AnchorSequenceTestScope.kt:56` | the public `AnchorStepScope` | Imported by `features/counter/.../CounterSequenceTest.kt:12`, `features/main/.../MainSequenceTest.kt:21` and `anchor-test/src/commonTest/.../SequenceTest.kt:7`, but hidden in another class's file |
| `anchor/.../RememberAnchorScope.kt:46-66` | `internal object AnchorRuntimeScope`, the runtime factory, which sits in the public-package file | Runtime machinery lives outside `internal/`. Its users are `viewmodel/ViewModelProvider.kt:4` and iosMain `RememberAnchor.kt:33` |
| `anchor/.../Anchor.kt:91-108` | `DefectAnchor` | The error capability is split up: `Raise` is in `Raise.kt:19`, the `DefectAnchor.orDie` extension is in `Recover.kt:104`, and `DefectAnchor` itself is in `Anchor.kt` |
| `anchor/.../internal/ContainedScope.kt` (entire file) and `AnchorScope.kt:41-62` | a `@PublishedApi internal interface ContainedScope` with **zero implementers**, and an `AnchorScope(containedScope)` factory with **zero callers** | This is dead code. The KDoc at `AnchorScope.kt:45-46` says it is "used internally by RememberAnchor", which is false. `CLAUDE.md:291` presents it as "Internal scope bridge". No file other than the two declaring ones has referenced these symbols in any release from `0.0.10` through `0.1.8` (checked per tag with `git grep`). The last caller was `0.0.9`, where `RememberAnchor` did `LocalAnchor provides AnchorScope(anchorScope)` (commit `48a6a3c`) |
| `CLAUDE.md:281-315` "Module Structure" | a file list | It lists no packages. It omits `Raise.kt`, `Recover.kt`, `AnchorExceptions.kt`, `BaseAnchorScope.kt`, `ErrorScope.kt`, `RememberAnchorScope.kt`, `internal/ErrorHandling.kt`, `internal/NonFatal.kt`, `internal/RaiseScope.kt` and `AnchorConsumer.kt`. It lists dead `ContainedScope.kt` |

### 1.3 What is and is not free to move: measured, not assumed

**Tooling in use:** none. The repo has no `.api` files, no `apiDump`/`apiCheck` tasks, no
binary-compatibility-validator plugin and no `abiValidation` block (`git ls-files | grep -E '\.api$'`
returns nothing, and `git grep -i abiValidation` over `*.kts`/`*.toml` returns nothing). The project
is on Kotlin **2.4.0** (`gradle/libs.versions.toml:15`). KGP 2.4.0 ships ABI validation built in
(`kotlin { abiValidation {} }`, `@ExperimentalAbiValidation`, "@since 2.4.0" in
`kotlin-gradle-plugin-api-2.4.0` sources, `org/jetbrains/kotlin/gradle/dsl/abi/AbiValidationExtension.kt`).
It provides the tasks `checkKotlinAbi`, `updateKotlinAbi` and `internalDumpKotlinAbi`, writes
`api/desktop/<module>.api` (JVM) and `api/<module>.klib.api` (iOS klibs), and `checkKotlinAbi` runs
as part of `./gradlew build`.

**Experiment**: in a disposable worktree at `0bc430c` I enabled `abiValidation {}` in
`dev.kioba.kmp-library.gradle.kts` (not committed), ran `updateKotlinAbi` for `anchor`,
`anchor-compose` and `anchor-test`, linked `:anchor:linkDebugFrameworkIosSimulatorArm64` to capture
the Objective-C header, then applied moves and diffed all three surfaces:

| Exp | Change | JVM `.api` | klib `.api` | ObjC/Swift header |
|-----|--------|-----------|-------------|-------------------|
| E1 | Split the marker interfaces and objects out of `Anchor.kt` into a new file, same package | no diff | no diff | no diff |
| E2 | Move top-level `getOrNull`/`getErrorOrNull` out of `Recover.kt` into a new file | **BREAK**: `RecoverKt` → `RecoverAccessorsKt` | no diff | **BREAK**: `RecoverKt` → `RecoverAccessorsKt` |
| E3 | Rename `anchor-compose/.../LocalScope.kt` → `LocalAnchor.kt` | **BREAK**: `LocalScopeKt.getLocalAnchor` → `LocalAnchorKt` | no diff | n/a (`@PublishedApi` is not ObjC-exported) |
| E4 | Rename iosMain `RememberAnchor.kt` → `IosAnchor.kt` | n/a | **no diff, so the klib dump MISSES this** | **BREAK**: `swift_name("RememberAnchorKt")` → `"IosAnchorKt"`. This breaks `iosApp/iosApp/ViewModelProtocol.swift:34` (`RememberAnchorKt.rememberAnchor(`) |
| E5 | Change package `internal` → `runtime` for `ContainedScope` | **BREAK** | **BREAK** | n/a |
| **M1–M5** | the proposed move set (§3) | **no diff** | **no diff** | **no diff** (0 header lines differ) |
| M7 | delete `ContainedScope` and the `AnchorScope()` factory | removes `AnchorScopeKt`, `internal/ContainedScope`, `internal/ContainedScopeKt` | removes the matching 3 declarations | no diff |

Also checked: `HandleSignal` is `public inline` with `reified T`, so the JVM backend emits it as
`ACC_PUBLIC, ACC_STATIC, ACC_FINAL, ACC_SYNTHETIC` (checked with `javap -v` on `HandleSignalKt.class`).
Callers therefore always inline it, and moving it between files is binary-neutral. The same holds for
`RememberAnchor`, `runAnchorTest` and `runAnchorSequenceTest`, none of which appear in the JVM dump.

With M1–M5 and M7 applied, the worktree ran `./gradlew :anchor:desktopTest :anchor-compose:desktopTest :anchor-test:desktopTest :features:counter:iosSimulatorArm64Test :features:main:iosSimulatorArm64Test`
and the build succeeded: `anchor` passed 90 tests and `anchor-test` passed 73 tests, with 0 failures.
`./gradlew build` also exited 0 once two Gradle cache-lock timeouts caused by a parallel
build had been rerun. See the plan's Investigation notes.

**Rules that follow from this:**

1. **Public package or FQN change**: source- and binary-breaking for every consumer. The samples
   alone have 27 import lines of root-package `dev.kioba.anchor` types, plus 10 from `.compose`,
   7 from `.test` and 2 from `.test.scopes` (`git grep` over `features/` and `androidApp/`).
2. **Classes, interfaces, objects and typealiases moving between files in the same package**:
   free on JVM, klib and ObjC.
3. **A top-level non-reified function or property moving between files, or its file being
   renamed**: breaks the JVM facade (`<File>Kt`) whenever the declaration is public or
   `@PublishedApi`. It also breaks the ObjC/Swift facade when the declaration is public in `anchor`.
   `@PublishedApi internal` counts as ABI because consumers' inlined bytecode calls it.
4. **Plain `internal` (not `@PublishedApi`)**: free to move anywhere, including across packages.
5. **The klib dump alone does not catch Swift facade renames.** The ObjC header must be diffed too.

**File names that are pinned today** (renaming them, or moving their top-level functions out, breaks
consumers):

- `anchor`: `Recover.kt` (JVM and ObjC), `internal/ErrorHandling.kt` (JVM and ObjC),
  `internal/NonFatal.kt` (JVM, all four source sets), `viewmodel/ViewModelProvider.kt` (JVM and
  ObjC), `iosMain/RememberAnchor.kt` (ObjC/Swift). Also `AnchorScope.kt` and
  `internal/ContainedScope.kt` until M7 deletes their facades.
- `anchor-compose`: `AnchorAction.kt`, `AnchorConsumer.kt`, `LocalScope.kt`, `LocalSignal.kt`,
  `RememberAnchor.kt` (all JVM)
- `anchor-test`: `scopes/AnchorTestScope.kt` (JVM, `@PublishedApi` `assertHandlers`)

## 2. Goals and non-goals

**Goals**

- G1: Each public class or interface that consumers import by name (`HandleSignal`,
  `AnchorStateScope`, `AnchorStepScope`) lives in a file named after it, where that move is
  ABI-neutral.
- G2: The error capability sits together: `DefectAnchor` moves next to `Raise`. Runtime machinery
  sits in `internal/`: `AnchorRuntimeScope` moves there.
- G3: Remove the dead `ContainedScope` and `AnchorScope(containedScope)` factory, together with their
  false KDoc. This step is gated because it changes the ABI dump.
- G4: A documented package map in `CLAUDE.md`, covering packages, their contents, where new code
  goes, and the pinned-file list with the reason for each.
- G5: No consumer-visible change, shown by zero diff in the JVM dump, the klib dump and the ObjC
  header. M7 is the only exception: it removes `@PublishedApi` symbols that no release since `0.0.9`
  referenced.

**Non-goals**

- Any public package or FQN change (`core/`, `runtime/` packages).
- Renaming pinned files (`LocalScope.kt`, iosMain `RememberAnchor.kt`, and the others listed above).
  These are documented instead.
- Re-splitting the markers out of `Anchor.kt`. That would recreate the `Anchor.kt`/`AnchorMarkers.kt`
  split that #142 complained about and that #152 deliberately merged.
- Gating the public helpers in `internal/ErrorHandling.kt`. That is plan 007.
- User-facing docs (`docs/*.md`, `AGENTS.md`), which belong to #140 and plans 012 and 013.
- The `anchorS` root-project rename, which is plan 016.
- Changes to sample or feature modules (plan 015).

## 3. Proposed design

Every move below is ABI-neutral per §1.3 (M1–M5 were verified together):

| ID | Move | Kind |
|----|------|------|
| M1 | `DefectAnchor` (`Anchor.kt:91-108`) → end of `Raise.kt` | interface, same package |
| M2 | `AnchorRuntimeScope` (`RememberAnchorScope.kt:46-66`) → new `internal/AnchorRuntimeScope.kt`, package `dev.kioba.anchor.internal`. Update the imports in `viewmodel/ViewModelProvider.kt` and iosMain `RememberAnchor.kt` | plain `internal` object |
| M3 | `AnchorStepScope` (`AnchorSequenceTestScope.kt:55-95`) → new `scopes/AnchorStepScope.kt` | class, same package |
| M4 | `HandleSignal` (`LocalSignal.kt:23-52`) → new `compose/HandleSignal.kt`. `LocalSignals` **stays** in `LocalSignal.kt`, because `LocalSignalKt.getLocalSignals` is pinned | reified inline function, synthetic on JVM |
| M5 | `AnchorStateScope` and `AnchorStateScopeImpl` (`compose/RememberAnchor.kt:21-73`) → new `compose/AnchorStateScope.kt`. `PreviewAnchor` stays, pinned by `RememberAnchorKt.PreviewAnchor` | interface and class, same package |
| M7 | delete `internal/ContainedScope.kt` and `AnchorScope.kt:41-62`, plus the 2 imports at lines 3-4 | gated removal of dead `@PublishedApi` symbols |
| D1 | rewrite `CLAUDE.md` "Module Structure" as a package map plus a pinned-file table | docs |
| B0 | *optional, gated*: commit KGP `abiValidation {}` and the `.api` dumps so that `./gradlew build` enforces G5 permanently | build policy |

### Alternatives considered

| Alternative | Rejected because |
|---|---|
| The issue's `core/ runtime/ compose/ testing/` package layout | `compose`/`testing` already exist as modules. `core/` renames the package of every public type, which is source- and binary-breaking for every consumer, with no functional gain. The roadmap (#237) already rates #142 "internal-only, low user-visible value" |
| Directories `core/` etc. while keeping `package dev.kioba.anchor` | Directory and package would disagree. This contradicts the Kotlin coding conventions (directory structure follows packages) and triggers IDE "package directive doesn't match file location" warnings. It would add confusion |
| Split `Anchor.kt` into `Markers.kt` and `Capabilities.kt` (ABI-neutral, E1) | This recreates the pre-#152 two-file split that the issue itself calls confusing. At 283 lines `Anchor.kt` is one navigable "core concepts" file. The marker decision itself is pending in plan 020 |
| Rename `LocalScope.kt` → `LocalAnchor.kt` with `@file:JvmName("LocalScopeKt")` | ABI-neutral, but it adds an annotation whose only purpose is to keep a misleading name alive, so clarity does not improve. Documenting the pin is cheaper |
| Rename iosMain `RememberAnchor.kt` (it collides in name with the Compose `RememberAnchor.kt`) | Swift-source-breaking (E4). The klib dump would not even flag it |
| `@file:JvmMultifileClass` to pull `Recover.kt` helpers into concept files | Adds build-level indirection for a cosmetic gain, and does not protect the ObjC facade |
| Close #142 as NOT A DEFECT | Most of the claims are indeed stale, but G1–G4 are real, cheap, and verifiable at zero ABI cost. The dead `ContainedScope` with its false KDoc is a concrete "confusing layout" defect in `internal/` |

## 4. Behavior and API changes

| Change | Source-compatible | Binary-compatible (JVM / klib / ObjC) | Classification |
|---|---|---|---|
| M1–M5 | yes (FQNs unchanged) | yes / yes / yes (measured) | **non-breaking** |
| M7 | yes (the symbols are `internal`) | The dump shows removal of `@PublishedApi` symbols. No inline function in any release from `0.0.10` to `0.1.8` referenced them, so no consumer bytecode can | **non-breaking in practice**. Maintainer GO required, because the ABI diff is non-empty |
| D1 | n/a | n/a | docs only |
| B0 | n/a | n/a | build policy: future PRs must run `updateKotlinAbi` on intended API changes |

No runtime behavior changes.

## 5. Acceptance criteria

- [ ] `HandleSignal.kt`, `AnchorStateScope.kt`, `scopes/AnchorStepScope.kt` and
      `internal/AnchorRuntimeScope.kt` exist. `DefectAnchor` is declared in `Raise.kt`.
- [ ] The JVM and klib ABI dumps for `anchor`, `anchor-compose` and `anchor-test` are **identical**
      to the pre-change baseline. If M7 is GO'd, the only difference is the removal of `AnchorScopeKt`,
      `internal/ContainedScope` and `internal/ContainedScopeKt`, and their klib equivalents.
- [ ] The ObjC header of `:anchor:linkDebugFrameworkIosSimulatorArm64` has 0 differing lines against
      the baseline.
- [ ] `CLAUDE.md` "Module Structure" names every package of the three published modules, what
      belongs in each, and the pinned-file list. It no longer lists `ContainedScope.kt` if M7 is GO'd.
      The line ``Published version: `x.y.z` `` still exists, because the CI `version_check` greps it.
- [ ] `./gradlew build` exits 0.

## 6. Open questions for the maintainer

1. **M7**: may the dead `ContainedScope` and `AnchorScope(containedScope)` be deleted in 0.1.9 (or
   0.1.10)? This is non-breaking in practice, but the ABI diff is non-empty.
2. **B0**: should KGP ABI validation be committed now? It needs `.api` files in git and an
   `updateKotlinAbi` step on API changes. CI (`pr_check.yml`) runs on `ubuntu-latest`, which cannot
   compile the iOS klibs, so klib checking there relies on `keepLocallyUnsupportedTargets` (default
   `true`, which infers from the committed dump). The macOS `ios_check.yml` only compiles. If yes:
   should plan 007's `@InternalAnchorApi` be excluded via `filters { exclude { annotatedWith.add(...) } }`?
3. Should the pinned-but-misleading names (`LocalScope.kt`, iosMain `RememberAnchor.kt`) get a
   **scheduled** rename at a release that already breaks the binary API anyway (for example, if plan
   020 removes the markers)? Or should they stay pinned forever?
4. After #142's residual work lands, close #142 with a pointer to this spec, and also tick it off in
   #145's P2 list?
