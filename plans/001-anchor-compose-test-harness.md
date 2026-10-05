# Plan 001: Establish a Compose UI test harness for anchor-compose

> **Triage note (2026-09-29, origin/master `0bc430c`)**: STALE API. `runComposeUiTest` is now deprecated and fails the warnings-as-errors build; use the v2 test API. Desktop tests also need a Main dispatcher (add `kotlinx-coroutines-swing` to desktopTest). This plan's test 4 excluded recomposition counting, which hid the #143 regression now tracked by plan 025. Plan 025 Step 1 contains a working desktop harness set-up; reconcile with it.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor-compose/ gradle/libs.versions.toml`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: tests
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

The `anchor-compose` module — the Compose integration layer of a published Kotlin
Multiplatform library — has **no test source set at all**. It contains
`RememberAnchor` (ViewModel wiring + state collection), `HandleSignal` (one-time
signal delivery), and `anchor()` (action callbacks). Two confirmed bugs live in
this module (signal conflation, unmemoized callbacks — plans 002 and 009), and
neither can be fixed with confidence until tests can run a real composition.
This plan creates the harness and a small set of baseline tests; plans 002 and
009 build on it.

## Current state

- `anchor-compose/src/` contains **only** `commonMain/kotlin/dev/kioba/anchor/compose/`
  with 4 files: `RememberAnchor.kt`, `AnchorAction.kt`, `LocalScope.kt`, `LocalSignal.kt`.
  There is no `commonTest` or `desktopTest` directory.
- `anchor-compose/build.gradle.kts` (entire current dependency block):

  ```kotlin
  plugins {
    id("dev.kioba.kmp-library")
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    id("dev.kioba.publish")
  }

  kotlin {
    sourceSets {
      commonMain {
        dependencies {
          api(projects.anchor)
          implementation(libs.lifecycle.viewmodel)
          implementation(libs.lifecycle.runtime)
          api(libs.compose.multiplatform.runtime)
        }
      }

      commonTest {
        dependencies {
          implementation(libs.kotlin.test)
        }
      }
    }
  }
  ```

  Note a `commonTest` dependency block already exists in the build file even
  though no test sources do.
- The `dev.kioba.kmp-library` convention plugin
  (`convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts`)
  registers a JVM target named `desktop`, Android (with `withHostTest {}`), and
  three iOS targets, with `explicitApi()` and `allWarningsAsErrors`.
- Key production code under test — `RememberAnchor.kt:150-172`:

  ```kotlin
  public inline fun <reified S, R> RememberAnchor(
    noinline scope: @DisallowComposableCalls RememberAnchorScope.() -> Anchor<R, S, *>,
    customKey: String? = null,
    crossinline content: @Composable AnchorStateScope<S>.() -> Unit,
  ) where R : Effect, S : ViewState {
    val key = customKey ?: S::class.qualifiedName.orEmpty()

    val anchorScope: ContainerViewModel<R, S, *> =
      viewModel(
        key = key,
        factory = anchorContainerViewModelFactory { scope() },
      )
    ...
  ```

  `ContainerViewModel` launches actions on `Dispatchers.Default`
  (`anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt:35-41`),
  so UI tests must *wait* for state changes rather than assert synchronously.
- Existing test convention in the repo: BDD-ish names in backticks, 2-space
  indent, `kotlin.test` assertions — see
  `anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableBasicTest.kt` for style.
- A sample anchor to model fixtures on:
  `features/counter/src/commonMain/kotlin/dev/kioba/anchor/features/counter/data/` —
  but do NOT depend on `features/*` from `anchor-compose`; define small local
  fixtures in the test source set instead.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Build module | `./gradlew :anchor-compose:build` | exit 0 |
| Run new tests | `./gradlew :anchor-compose:desktopTest` | exit 0, all tests pass |
| Full check | `./gradlew build` | exit 0 |

## Scope

**In scope** (the only files you should modify/create):
- `anchor-compose/build.gradle.kts` (add test dependencies only)
- `anchor-compose/src/commonTest/kotlin/dev/kioba/anchor/compose/**` (create)
- `gradle/libs.versions.toml` (only if a new version/library alias is required)

**Out of scope** (do NOT touch, even though they look related):
- Any file in `anchor-compose/src/commonMain/` — this plan adds tests for
  current behavior; production fixes are plans 002 and 009.
- `anchor/` module sources.
- The `dev.kioba.kmp-library` convention plugin.

## Git workflow

- Branch: `test/anchor-compose-harness`
- Commit style: gitmoji + imperative, e.g. `🧪 Add Compose UI test harness for anchor-compose`
  (matches repo history, e.g. `🧪 Add sequence testing DSL to anchor-test (#214)`).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add Compose UI-test dependencies

In `anchor-compose/build.gradle.kts`, extend the existing `commonTest`
dependencies block and add a `desktopTest` block:

```kotlin
commonTest {
  dependencies {
    implementation(libs.kotlin.test)
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    implementation(compose.uiTest)
    implementation(libs.kotlin.coroutinesTest)
  }
}
desktopTest {
  dependencies {
    implementation(compose.desktop.currentOs)
  }
}
```

The `compose.uiTest` and `compose.desktop.currentOs` accessors come from the
already-applied `compose.multiplatform` plugin. If `libs.kotlin.coroutinesTest`
does not exist in `gradle/libs.versions.toml`, check the `[libraries]` section
for the alias used by `anchor-test/build.gradle.kts` (it declares
`implementation(libs.kotlin.coroutinesTest)`) and reuse it.

**Verify**: `./gradlew :anchor-compose:build` → exit 0.

### Step 2: Create test fixtures

Create `anchor-compose/src/commonTest/kotlin/dev/kioba/anchor/compose/TestFixtures.kt`:

- `data class TestState(val count: Int = 0, val label: String = "") : ViewState`
- `sealed interface TestSignal : Signal { data class Toast(val n: Int) : TestSignal }`
- A factory: `fun RememberAnchorScope.testAnchor(): Anchor<EmptyEffect, TestState, Nothing> = create(initialState = { TestState() }, effectScope = { EmptyEffect })`
- Suspend action extensions used by tests, e.g.
  `suspend fun Anchor<EmptyEffect, TestState, Nothing>.increment() { reduce { copy(count = count + 1) } }`
  and one that posts a signal: `suspend fun ...postToast(n: Int) { post { TestSignal.Toast(n) } }`

**Verify**: `./gradlew :anchor-compose:desktopTest` → exit 0 (compiles, zero tests yet is fine).

### Step 3: Baseline behavior tests

Create `anchor-compose/src/commonTest/kotlin/dev/kioba/anchor/compose/RememberAnchorTest.kt`
using `runComposeUiTest` (`@OptIn(ExperimentalTestApi::class)`,
`androidx.compose.ui.test.*`). Tests:

1. `RememberAnchor renders initial state` — set content with
   `RememberAnchor(scope = { testAnchor() }) { Text("count:${'$'}{state.count}") }`,
   assert node with text `count:0` exists.
2. `anchor callback executes action and updates state` — a `Button`
   wired to `anchor(...)` for `increment`; perform click; then
   `waitUntil(timeoutMillis = 5_000) { ... }` for text `count:1`
   (the action runs on `Dispatchers.Default`, so use `waitUntil`, not an
   immediate assertion).
3. `HandleSignal receives a posted signal` — compose
   `HandleSignal<TestSignal.Toast> { received += it.n }` inside the
   `RememberAnchor` content, trigger an action that posts one signal via a
   button click, `waitUntil` the `received` list contains it.
4. `collectState only exposes selected slice` — render
   `collectState { it.count }`, update an unrelated field (`label`), assert
   the count text is unchanged (smoke test that selection works; precise
   recomposition counting is out of scope).

Important: each test that needs a distinct ViewModel must pass a unique
`customKey` to `RememberAnchor` (the default key is the state class qualified
name, and `runComposeUiTest` environments may share a ViewModelStore between
scenarios within a test).

**Verify**: `./gradlew :anchor-compose:desktopTest` → exit 0, 4 tests pass.

### Step 4: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

This plan *is* tests; see Step 3 for the case list. Model assertion style on
`anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableBasicTest.kt`
(kotlin.test, backtick names).

## Done criteria

- [ ] `anchor-compose/src/commonTest` exists with fixtures + ≥4 passing tests
- [ ] `./gradlew :anchor-compose:desktopTest` exits 0
- [ ] `./gradlew build` exits 0
- [ ] No files outside the in-scope list modified (`git status`)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back (do not improvise) if:

- `compose.uiTest` / `compose.desktop.currentOs` accessors do not resolve in
  this build (report the exact Gradle error and the compose plugin version from
  `gradle/libs.versions.toml` `compose-plugin`).
- `viewModel(...)` inside `RememberAnchor` throws in the `runComposeUiTest`
  environment (no ViewModelStoreOwner provided by the test host). If so, report —
  the fix likely requires wrapping content in a `CompositionLocalProvider` with a
  test ViewModelStoreOwner, which is a design decision for the advisor.
- Test 3 (HandleSignal) fails intermittently — that is plan 002's bug surfacing;
  report rather than weakening the test. Use a single signal, which should be
  reliable; only bursts are known-lossy.

## Maintenance notes

- Plans 002 and 009 add tests to this same source set; keep fixtures generic.
- When CI runs `./gradlew build`, desktop UI tests run headless — Skiko/AWT
  headless mode is handled by the Compose test framework; if CI fails with a
  display error, set `java.awt.headless=true` in the test JVM args.
- iOS/Android source-set tests are intentionally not added here; desktop is the
  fast, CI-friendly target for composition semantics.

## Investigation results (2026-09-29)

Executed on `test/001-anchor-compose-harness`, stacked on `origin/fix/022-anchor-action-type-resolution` (`2651e8f`, PR #271).

- **Drift** (`git diff --stat 492f7bc..HEAD -- anchor-compose/ gradle/libs.versions.toml`): 7 files. The drift doesn't change the plan's intent, only where the harness lives and how it's set up.
  - #271 already added the desktop UI-test set-up to `anchor-compose/build.gradle.kts`. `desktopTest` has `libs.compose.multiplatform.uiTest`, `compose.desktop.currentOs`, `libs.kotlin.coroutinesTest` and `libs.kotlin.coroutinesSwing`, and the two aliases are in `libs.versions.toml`. This is the same set-up as plan 025 Step 1, so Step 1 needs no build change.
  - #271 added `desktopTest/.../NestedAnchorTest.kt`, which uses `androidx.compose.ui.test.v2.runComposeUiTest`.
  - `AnchorConsumer.kt` has landed. `anchor()` now resolves anchors by ViewState through `LocalAnchors` and remembers its callbacks.
- **commonTest vs desktopTest**: `commonTest` sources also run on `testAndroidHostTest` and `iosSimulatorArm64Test`. In `anchor/build/test-results/`, `CancellableBasicTest` from `commonTest` runs under both. Compose UI tests can't run on the Android host without Robolectric, and this plan excludes Android and iOS tests. So the harness goes in `desktopTest`, next to `NestedAnchorTest`.
- **v2 test host (CMP 1.11.1)**:
  - It provides its own `ViewModelStoreOwner` and a RESUMED `LocalLifecycleOwner` (`DefaultArchitectureComponentsOwner`), and tests can't move that lifecycle. `runOnUiThread` is the Swing EDT, which is also `Dispatchers.Main` via coroutines-swing.
  - `waitForIdle()` doesn't pump EDT work. Collectors on `Main.immediate` resume through the EDT queue, so a "did not happen" assertion needs `runOnUiThread {}` + `waitForIdle()` first. The harness's `drainUiThread()` does this.
- **Lifecycle below STARTED (for plan 002)**: `TestLifecycleOwner` uses `LifecycleRegistry.createUnsafe`, is installed via `LocalLifecycleOwner`, and is moved on the EDT. A scratch probe on current code (not committed) behaved as follows:
  - `HandleSignal` handled Toast(1) while RESUMED.
  - Toast(2), posted while CREATED, was dropped and never delivered after returning to RESUMED.
  - Toast(3), posted after resume, was delivered. The final list was `[1, 3]` in 3/3 runs.
  - Plan 002's maintenance note says signals emitted while stopped "sit in the 64-buffer and deliver on restart". That doesn't hold for any collector that unsubscribes below STARTED: `replay = 0` and no subscriber means the signal is dropped. This is plan 024's territory.
- **Recomposition counting**: `CompositionCounter.record()` placed in the `collectState` caller counts `expected:<1> but was:<4>` after 3 label-only updates, in 5/5 runs. This reproduces #143 (plan 025) with this harness. The committed test 4 counts the slice's *consumer* (0 extra recompositions), which is current and correct behavior. The caller-level assertion is left to plan 025, since `commonMain` is out of scope here.

## Execution results (2026-09-29)

- **Status**: STOPPED before push. The code is done and committed locally, but the `./gradlew build` gate is blocked by the environment.
- **Branch**: `test/001-anchor-compose-harness` (local only, commit `4aff2b0`), based on `origin/fix/022-anchor-action-type-resolution` (`2651e8f`, PR #271). Not pushed, and no PR opened.
- **Blocker**: Xcode.app was updated to 27.0 (27A266a) at 17:29 during the run. `/Library/Preferences/com.apple.dt.Xcode` records the license as agreed only for 26.3. Every `xcrun` call now exits 69 ("You have not agreed to the Xcode license agreements"). `./gradlew build --continue` fails with exactly 30 failures, all of them iOS `link*Ios*` tasks or `iosSimulatorArm64Test` `device` evaluation, and every one is an xcrun/license error. Everything else passed, including `:anchor-compose:check`, Android lint and all `testAndroidHostTest`/`desktopTest` tasks. **Maintainer action**: `sudo xcodebuild -license accept`, then rerun `./gradlew build` on the branch, push it, and open the stacked PR (`--base fix/022-anchor-action-type-resolution`, depends on #271).
- **Files**:
  - Created `anchor-compose/src/desktopTest/kotlin/dev/kioba/anchor/compose/{TestFixtures,ComposeTestHarness,RememberAnchorTest}.kt`.
  - No build-file or version-catalog change: #271's `desktopTest` dependencies already cover what the harness needs.
- **Deviations**:
  1. The harness lives in `desktopTest`, not `commonTest`. `commonTest` also runs on the Android host (which would need Robolectric) and the iOS simulator. Plan 002's `HandleSignalTest` should go in `desktopTest` too.
  2. The tests use v2 `runComposeUiTest`, per the triage note.
  3. They use foundation `BasicText` + `clickable` (`TestButton`) instead of a material `Button`.
  4. Test 4 now counts recompositions of the slice's consumer (asserted: no recomposition on label-only changes).
  5. There is a 5th test for lifecycle below STARTED, which validates the harness for plan 002.
  6. Extra fixtures: `setLabel`, `postSignals(List<TestSignal>)` and `TestSignal.Other` (for plan 002's burst and other-type cases).
- **Test evidence**:
  - `./gradlew :anchor-compose:desktopTest`: 11/11 pass (`RememberAnchorTest` 5, `NestedAnchorTest` 6).
  - The same command with `--rerun`, run 30 times: 30/30 pass, no flakes, including the HandleSignal test.
  - Sensitivity checks (scratch, not committed): a caller-level `CompositionCounter` reproduces #143 (`expected:<1> but was:<4>`, 5/5). Without `moveLifecycleTo(CREATED)`, the paused-state assertion fails deterministically (5/5), so `drainUiThread` makes the negative assertion meaningful.
- **For plan 002**:
  - Use `TestLifecycleOwner` + `setContentWithLifecycle` + `moveLifecycleTo` to go below STARTED.
  - Use `postSignals(listOf(...))` for ordered bursts.
  - Use `drainUiThread()` before asserting that a signal was not handled.
- **For plan 025**: reuse `CompositionCounter` and the fixtures. `collectState` still returns a stale selector value on this base, and the caller recomposes on every change.
