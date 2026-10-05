# Plan 007: Gate dev.kioba.anchor.internal helpers behind a @RequiresOptIn annotation

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt anchor-test/src/commonMain/`
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

`dev.kioba.anchor.internal.ErrorHandling.kt` declares `safeExecute`,
`catchDomainError`, and `catchDefects` as **`public`** in a published artifact.
Consumers can (and eventually will) call them, freezing error-routing
internals into the de-facto API before 1.0. They cannot simply be made
`internal`: the separate published module `anchor-test` calls `safeExecute`
from `AnchorTestScope.kt:82` and `AnchorSequenceTestScope.kt:129`. The Kotlin
ecosystem convention for "public for technical reasons, not for you" is a
`@RequiresOptIn`-gated annotation (cf. `kotlinx.coroutines.InternalCoroutinesApi`).

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt:9,23,36` —
  three `public suspend inline fun` declarations:

  ```kotlin
  public suspend inline fun <R, S, Err> catchDomainError(...)
  public suspend inline fun <R, S, Err> catchDefects(...)
  public suspend inline fun <R, S, Err> safeExecute(...)
  ```

- All cross-file users (verified by grep at planning time):
  - `anchor` commonMain: `SubscriptionDsl.kt:50`, `ContainerViewModel.kt:37,46`,
    `AnchorRuntime.kt:104`, `ContainedScope.kt:29` (same module — no opt-in
    ripple beyond annotation propagation rules)
  - `anchor-test` commonMain: `AnchorTestScope.kt:82`,
    `AnchorSequenceTestScope.kt:129` (cross-module — needs opt-in)
  - tests: `anchor/src/commonTest/.../NonFatalTest.kt`, `ExecuteBoundaryTest.kt`,
    `anchor/src/desktopTest/.../NonFatalJvmTest.kt` (same-module tests — need opt-in)
- The build runs with `explicitApi()` and `allWarningsAsErrors` (from
  `convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts`), so an
  un-opted-in use fails the build — which is exactly the enforcement we want.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Affected modules | `./gradlew :anchor:build :anchor-test:build` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/InternalAnchorApi.kt` (create)
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt`
  (annotations only)
- The use-site files listed above (add `@OptIn(InternalAnchorApi::class)` /
  propagate `@InternalAnchorApi` as the compiler requires)

**Out of scope**:
- Changing any function body or signature in `ErrorHandling.kt`.
- Other `public` members of the `internal` package (e.g. `RaisedException`
  lives in `anchor-internal` and is genuinely public API for `catch` clauses —
  leave it).
- Plans 003/004 touch `AnchorRuntime.kt`/`ContainerViewModel.kt` too; if they
  have landed, the call sites may have moved lines — annotate wherever the
  calls now are.

## Git workflow

- Branch: `refactor/internal-api-opt-in`
- Commit style: gitmoji, e.g. `♻️ Gate internal error-handling helpers behind @InternalAnchorApi`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Create the opt-in annotation

`anchor/src/commonMain/kotlin/dev/kioba/anchor/InternalAnchorApi.kt`:

```kotlin
package dev.kioba.anchor

/**
 * Marks declarations that are internal to the Anchor library machinery.
 *
 * These APIs are public only so sibling Anchor modules can reach them. They
 * provide no compatibility guarantees and may change or disappear in any
 * release. Do not use them in application code.
 */
@RequiresOptIn(
  message = "This is an internal Anchor API with no compatibility guarantees.",
  level = RequiresOptIn.Level.ERROR,
)
@Retention(AnnotationRetention.BINARY)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
)
public annotation class InternalAnchorApi
```

**Verify**: `./gradlew :anchor:compileKotlinDesktop` → exit 0.

### Step 2: Annotate the three helpers

Add `@InternalAnchorApi` to `catchDomainError`, `catchDefects`, `safeExecute`
in `ErrorHandling.kt` (import `dev.kioba.anchor.InternalAnchorApi`).

**Verify**: `./gradlew :anchor:compileKotlinDesktop` → now FAILS listing every
un-opted-in use site. That failure list is your worklist for step 3.

### Step 3: Opt in at every use site

At each compiler-reported site, prefer file-level
`@file:OptIn(InternalAnchorApi::class)` when a file has multiple uses
(`ExecuteBoundaryTest.kt` has ~15), otherwise declaration-level `@OptIn`.
Inline-function subtlety: for the `anchor`-module callers that are themselves
`public inline` (if any report "opt-in marker can leak"), follow the compiler's
guidance — propagate `@InternalAnchorApi` instead of `@OptIn` only on
declarations that are themselves internal-machinery (e.g. inside
`internal`/`@PublishedApi` members); never put `@InternalAnchorApi` on a
genuinely public user-facing API like `SubscriptionsScope.anchor` — if the
compiler forces that, STOP (see below).

**Verify**: `./gradlew :anchor:build :anchor-test:build` → exit 0.

### Step 4: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

No new behavior; compilation is the test. Existing suites must stay green:
`./gradlew :anchor:desktopTest :anchor-test:desktopTest` → exit 0.

## Done criteria

- [ ] `InternalAnchorApi` exists with ERROR level and KDoc
- [ ] All three `ErrorHandling.kt` functions are annotated
- [ ] `grep -rn "OptIn(InternalAnchorApi" anchor anchor-test --include="*.kt" | wc -l` ≥ 6
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- The compiler requires propagating `@InternalAnchorApi` onto a user-facing
  public API (e.g. `Flow.anchor` in `SubscriptionDsl.kt` or anything in
  `anchor-test`'s public DSL). That would push the marker onto consumers —
  needs an advisor decision (likely restructuring the call instead).
- More cross-module users than `anchor-test`'s two files appear (e.g.
  `features/` or `anchor-compose` started using the helpers since planning).

## Maintenance notes

- Future internal-but-public members (e.g. `@PublishedApi` runtime internals)
  can adopt the same annotation; this plan deliberately starts with only
  `ErrorHandling.kt` to keep the diff reviewable.
- Reviewer: confirm the annotation lives in the root package
  (`dev.kioba.anchor`), not `.internal`, so the opt-in itself is a stable name.
- If/when a binary-compatibility validator is added (good 1.0-prep), exclude
  `@InternalAnchorApi`-annotated symbols from its public dump.
