# Plan 015: Extract a feature-module convention plugin to kill triplicated build config

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- features/ convention-plugins/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P3
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: tech-debt
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

`features/counter`, `features/config`, and `features/main` each hand-roll the
same multiplatform setup — plugin block, `android { namespace/compileSdk/minSdk }`,
per-compilation `JvmTarget.JVM_17`, iOS target list — that the library modules
get from the `dev.kioba.kmp-library` convention plugin. Every toolchain change
must be replicated 3× (and 4× counting `features/resources` if applicable),
and the copies have already started needing lockstep edits (6 of the last 100
commits touched each feature build file — same churn count, same changes).
These sample modules are also the templates users copy; they should model the
clean setup.

## Current state

- `features/counter/build.gradle.kts:1-30` (config and main are near-identical;
  diff them in step 1):

  ```kotlin
  plugins {
    alias(libs.plugins.android.multiplatformLibrary)
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
  }

  kotlin {
    explicitApi()

    android {
      namespace = "dev.kioba.anchor.features.counter"
      compileSdk = libs.versions.android.compileSdk.get().toInt()
      minSdk = libs.versions.android.minSdk.get().toInt()

      compilations.configureEach {
        compileTaskProvider.configure {
          compilerOptions {
            jvmTarget.set(
              org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            )
          }
        }
      }
    }

    listOf(
      iosX64(),
      ...
  ```

- The exemplar convention plugin —
  `convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts` —
  already encodes: plugins (`org.jetbrains.kotlin.multiplatform`,
  `com.android.kotlin.multiplatform.library`), `explicitApi()`,
  `allWarningsAsErrors`, `jvm("desktop")`, android block with
  `namespace = "dev.kioba.${project.name.replace("-", ".")}"`, `withHostTest {}`,
  JVM_17, and the three iOS targets with framework config. Feature modules
  differ from it in: namespace pattern
  (`dev.kioba.anchor.features.<name>`), compose plugins, possibly no
  publishable framework config, possibly no `allWarningsAsErrors`.
- `convention-plugins/build.gradle.kts` declares the included build's plugin
  classpath — read it in step 2; compose plugins must be added there to be
  usable from a convention script.
- `convention-plugins/settings.gradle.kts` exists (included build).
- Note `features/counter/build.gradle.kts` currently calls `explicitApi()` —
  decide in step 3 whether feature samples keep it (they currently do; keep
  behavior identical, this is a refactor).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Feature tests | `./gradlew :features:counter:build :features:config:build :features:main:build` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `convention-plugins/build.gradle.kts` (classpath additions)
- `convention-plugins/src/main/kotlin/dev.kioba.feature-library.gradle.kts` (create)
- `features/counter/build.gradle.kts`, `features/config/build.gradle.kts`,
  `features/main/build.gradle.kts` (collapse to convention + per-module deps)
- `features/resources/build.gradle.kts` ONLY if step 1's diff shows it shares
  the same shape (it is an Android resources module and may differ — check)

**Out of scope**:
- `dev.kioba.kmp-library.gradle.kts` and the published modules' builds.
- Any change to dependencies blocks' *content* (each feature keeps its own deps).
- `androidApp`, `umbrella`.

## Git workflow

- Branch: `refactor/feature-convention-plugin`
- Commit style: gitmoji, e.g. `♻️ Extract feature-library convention plugin`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Map the actual deltas

`diff features/counter/build.gradle.kts features/config/build.gradle.kts` and
vs `main`. List what is identical (goes into the convention) and what varies
(stays per-module: namespace suffix, dependencies, any feature-specific
config). Capture the baseline:
`./gradlew :features:counter:build :features:config:build :features:main:build` → exit 0.

**Verify**: baseline green; delta list in your report.

### Step 2: Create `dev.kioba.feature-library.gradle.kts`

In `convention-plugins/`: read `build.gradle.kts` there and mirror how the
existing plugins' implementation classpath is declared to add the compose
compiler + compose multiplatform plugin artifacts. New script applies the
shared plugin set and target config from step 1's "identical" list, deriving
namespace as `"dev.kioba.anchor.features.${project.name}"`.

**Verify**: `./gradlew :features:counter:tasks -q` after switching counter
only (step 3 does the switch — it's fine to iterate counter-first).

### Step 3: Collapse the three feature build files

Each becomes:

```kotlin
plugins {
  id("dev.kioba.feature-library")
}

kotlin {
  sourceSets {
    commonMain { dependencies { /* existing deps, unchanged */ } }
    commonTest { dependencies { /* existing deps, unchanged */ } }
    ...
  }
}
```

Behavior must be identical — compare resolved config:
`./gradlew :features:counter:build` etc., and confirm test counts in
`features/*/build/test-results/` match step 1's baseline.

**Verify**: all three feature builds green; test counts unchanged.

### Step 4: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

Pure build refactor: gates are identical build outcomes and identical test
counts before/after (step 1 baseline vs step 3).

## Done criteria

- [ ] Three feature build files contain no android/iOS target boilerplate
- [ ] `dev.kioba.feature-library.gradle.kts` exists in convention-plugins
- [ ] Test counts per feature module unchanged
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- The compose plugins cannot be applied from a precompiled-script convention
  plugin in this Gradle/AGP combination (a known friction point — report the
  exact error and Gradle/AGP/compose-plugin versions).
- The feature modules' android blocks turn out to differ in ways the delta
  list didn't anticipate (e.g. `main` has extra source-set wiring).
- `features/resources` shares the shape but applying the convention changes
  its behavior — leave it out and note it.

## Maintenance notes

- ROADMAP #142 (reorganize files by concept) touches the same modules — land
  this first or coordinate; both are internal-only.
- Reviewer: confirm the convention does NOT add `dev.kioba.publish` — feature
  samples must stay unpublished.
- Future toolchain bumps (JVM target, SDK levels) now happen in two convention
  files (`kmp-library`, `feature-library`); consider extracting the shared
  android-block helper if a third convention ever appears — not now.
