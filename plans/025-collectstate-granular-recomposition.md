# Plan 025: Make `collectState` recompose only on selected-slice changes and honor selector changes

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report. Do not improvise. When done, update the status row for this plan
> in `plans/README.md`, unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor-compose/ gradle/libs.versions.toml`
> Expected benign drift:
> - Plan 001 may have added an `anchor-compose` test source set and dependencies. Reuse them and skip Step 1's duplicates.
> - Plan 029 B5 may have moved `AnchorStateScope`/`AnchorStateScopeImpl` from `RememberAnchor.kt` into `compose/AnchorStateScope.kt`. Apply Step 3 there instead.
>
> Any other change to `collectState` is a STOP. Compare against "Current state" first.

## Status

- **Priority**: P1
- **Effort**: S
- **Risk**: LOW. One function body changes, with no signature change. The prototype passed.
- **Depends on**: none (self-contained `desktopTest` harness). Plan 001 complements this plan: whichever lands second reconciles the test set-up.
- **Category**: bug
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: [plans/specs/025-collectstate-granular-recomposition.md](specs/025-collectstate-granular-recomposition.md)

## Why this matters

`collectState { it.count }` is Anchor's advertised way to "observe only the
state properties you need" (`README.md:19`, `docs/compose.md:69-81`, KDoc
`RememberAnchor.kt:41`). On master it does two things wrong:

1. The caller recomposes on **every** state change. The full state is read inside a
   non-restartable composable, so the caller's scope is invalidated.
2. It returns a **stale value** when the selector changes but the state does not. Its
   `remember` is keyed on the stable `rememberUpdatedState` holder, not on the
   selector.

Both were confirmed by failing tests at `0bc430c` (spec §1). Issue #143 was closed
by PR #152 as if this worked.

## Current state

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt:58-73`:

  ```kotlin
  @PublishedApi
  internal class AnchorStateScopeImpl<S : ViewState>(
    private val stateFlow: StateFlow<S>,
  ) : AnchorStateScope<S> {
    override val state: S
      @Composable get() = stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate).value

    @Composable
    override fun <T> collectState(
      selector: (S) -> T,
    ): T {
      val updatedSelector = rememberUpdatedState(selector)
      val state by stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
      return remember(state, updatedSelector) { updatedSelector.value(state) }
    }
  }
  ```

- KDoc contract, `RememberAnchor.kt:38-55`: "Collects a specific part of the [ViewState]. Recomposes only when the selected value (returned by the [selector]) changes."
- `anchor-compose/build.gradle.kts` has a `commonTest` block with `libs.kotlin.test` only. No test sources exist.
- `gradle/libs.versions.toml`: `compose-plugin = "1.11.1"` (line 9), `kotlinxCoroutines = "1.11.0"` (line 16), `kotlin-coroutinesTest` alias (line 57).
- Harness facts verified at `0bc430c` in a disposable worktree:
  - `androidx.compose.ui.test.runComposeUiTest` is **deprecated** in CMP 1.11.1, so compilation fails under `allWarningsAsErrors`. Use `androidx.compose.ui.test.v2.runComposeUiTest`.
  - Without `kotlinx-coroutines-swing`, `collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)` throws "Dispatchers.Main was accessed when the platform dispatcher was absent".
  - v2 `runComposeUiTest` on desktop supplies `LocalLifecycleOwner` and `LocalViewModelStoreOwner` itself, so `RememberAnchor` works unwrapped.
  - Actions run on `Dispatchers.Default` (`ContainerViewModel.kt:36`). Use `waitUntil`, not immediate asserts.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| New tests | `./gradlew :anchor-compose:desktopTest --tests 'dev.kioba.anchor.compose.CollectStateTest'` | exit 0 |
| Module tests | `./gradlew :anchor-compose:desktopTest` | exit 0 |
| iOS compile | `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

If another Gradle process holds the build cache lock, add `--no-build-cache`.

## Scope

**In scope**:
- `anchor-compose/build.gradle.kts` (desktopTest dependencies only)
- `gradle/libs.versions.toml` (two library aliases)
- `anchor-compose/src/desktopTest/kotlin/dev/kioba/anchor/compose/CollectStateTest.kt` (create)
- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt`: `AnchorStateScopeImpl.collectState` body, its import list, and the `AnchorStateScope` KDoc. If 029 B5 landed, edit the same code in `AnchorStateScope.kt` instead.

**Out of scope**:
- The `state` property's behavior. It stays "recompose on any change".
- `docs/`. The docs already describe the fixed behavior, so no `llms-full.txt` regeneration is needed.
- `HandleSignal` and `anchor()` (plans 002, 009, 022).
- Moving the harness to `commonTest` (plan 001).

## Git workflow

- Branch: `fix/collectstate-granular-recomposition`
- Commit style: gitmoji, e.g. `🧪 Add collectState recomposition regression tests`, then `🐛 Back collectState with derivedStateOf`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add the desktop UI-test dependencies

In `gradle/libs.versions.toml` `[libraries]`, add:

```toml
compose-multiplatform-uiTest = { module = "org.jetbrains.compose.ui:ui-test", version.ref = "compose-plugin" }
kotlin-coroutinesSwing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "kotlinxCoroutines" }
```

In `anchor-compose/build.gradle.kts`, inside `kotlin { sourceSets { ... } }` after `commonTest`:

```kotlin
    val desktopTest by getting {
      dependencies {
        implementation(libs.compose.multiplatform.uiTest)
        implementation(compose.desktop.currentOs)
        implementation(libs.kotlin.coroutinesTest)
        implementation(libs.kotlin.coroutinesSwing)
      }
    }
```

This set of coordinates was verified. If plan 001 already added equivalents,
keep theirs and add only what is missing. `kotlinx-coroutines-swing` is the
piece plan 001 lacks.

**Verify**: `./gradlew :anchor-compose:desktopTest` → exit 0 (no tests yet is fine).

### Step 2: Write the failing regression tests first

Create `anchor-compose/src/desktopTest/kotlin/dev/kioba/anchor/compose/CollectStateTest.kt`:

```kotlin
package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals

data class ProbeState(
  val count: Int = 0,
  val label: String = "",
) : ViewState

typealias ProbeAnchor = Anchor<EmptyEffect, ProbeState, Nothing>

fun RememberAnchorScope.probeAnchor(): ProbeAnchor = create(initialState = ::ProbeState, effectScope = { EmptyEffect })

suspend fun ProbeAnchor.setLabel(label: String) {
  reduce { copy(label = label) }
}

suspend fun ProbeAnchor.inc() {
  reduce { copy(count = count + 1) }
}

@Composable
fun CountProbe(
  scope: AnchorStateScope<ProbeState>,
  compositions: AtomicInteger,
  lastCount: AtomicInteger,
) {
  compositions.incrementAndGet()
  lastCount.set(scope.collectState { it.count })
}

@Composable
fun OffsetProbe(
  scope: AnchorStateScope<ProbeState>,
  offset: Int,
  last: AtomicInteger,
) {
  last.set(scope.collectState { it.count + offset })
}

// Separate restart scope: lets the test wait for a label update without
// reading state inside CountProbe.
@Composable
fun LabelObserver(
  scope: AnchorStateScope<ProbeState>,
  lastLabel: AtomicReference<String>,
) {
  lastLabel.set(scope.state.label)
}

@OptIn(ExperimentalTestApi::class)
class CollectStateTest {
  @Test
  fun `caller does not recompose when an unselected field changes`() =
    runComposeUiTest {
      val compositions = AtomicInteger(0)
      val lastCount = AtomicInteger(-1)
      val lastLabel = AtomicReference("")
      var setLabel: ((String) -> Unit)? = null
      setContent {
        RememberAnchor(scope = { probeAnchor() }, customKey = "unrelated-field") {
          setLabel = anchor(ProbeAnchor::setLabel)
          CountProbe(this, compositions, lastCount)
          LabelObserver(this, lastLabel)
        }
      }
      waitForIdle()
      val baseline = compositions.get()
      repeat(3) { i ->
        setLabel!!("label-$i")
        waitUntil(timeoutMillis = 5_000) { lastLabel.get() == "label-$i" }
        waitForIdle()
      }
      assertEquals(baseline, compositions.get(), "caller recomposed for label-only changes")
    }

  @Test
  fun `selected value change is observed`() =
    runComposeUiTest {
      val compositions = AtomicInteger(0)
      val lastCount = AtomicInteger(-1)
      var increment: (() -> Unit)? = null
      setContent {
        RememberAnchor(scope = { probeAnchor() }, customKey = "selected-change") {
          increment = anchor(ProbeAnchor::inc)
          CountProbe(this, compositions, lastCount)
        }
      }
      waitForIdle()
      increment!!()
      waitUntil(timeoutMillis = 5_000) { lastCount.get() == 1 }
    }

  @Test
  fun `selector change is honored without a state change`() =
    runComposeUiTest {
      val last = AtomicInteger(-1)
      var offset by mutableIntStateOf(0)
      setContent {
        RememberAnchor(scope = { probeAnchor() }, customKey = "selector-change") {
          OffsetProbe(this, offset, last)
        }
      }
      waitForIdle()
      assertEquals(0, last.get())
      offset = 10
      waitForIdle()
      assertEquals(10, last.get(), "collectState returned a stale value after the selector changed")
    }
}
```

Test sources are compiled with `allWarningsAsErrors`. Keep them warning-free.
Unique `customKey`s keep the ViewModels separate.

**Verify**: `./gradlew :anchor-compose:desktopTest --tests 'dev.kioba.anchor.compose.CollectStateTest'`.
Expect exactly 2 failures:
- `caller does not recompose…`: expected `<1>` but was `<4>`, give or take the baseline.
- `selector change is honored…`: expected `<10>` but was `<0>`.

`selected value change is observed` passes.

### Step 3: Back `collectState` with `derivedStateOf`

Replace the `collectState` body in `AnchorStateScopeImpl`:

```kotlin
  @Composable
  override fun <T> collectState(
    selector: (S) -> T,
  ): T {
    val updatedSelector = rememberUpdatedState(selector)
    val state = stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val selected = remember(state) { derivedStateOf { updatedSelector.value(state.value) } }
    return selected.value
  }
```

Add `import androidx.compose.runtime.derivedStateOf`. Remove
`import androidx.compose.runtime.getValue`. On `0bc430c`, line 70 is the file's
only `by` delegate, and this change removes it. kotlinc does not flag unused
imports, but the repo's ktlint config (`.editorconfig:18`) does, so remove it by
hand.

**Verify**: `./gradlew :anchor-compose:desktopTest --tests 'dev.kioba.anchor.compose.CollectStateTest'` → all 3 pass.

### Step 4: Tighten the KDoc

In the `AnchorStateScope.collectState` KDoc (`RememberAnchor.kt:38-51`), keep
the example and state three things:
- The caller recomposes only when the selected value changes, compared with `equals`.
- The selector may capture composition values. A changed selector is applied on the next recomposition.
- For whole-state reads, use `state`, which recomposes on any change.

**Verify**: `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` → exit 0. The KDoc builds and `derivedStateOf` is common API.

### Step 5: Full gate

**Verify**: `./gradlew build` → exit 0.

## Test plan

Test-first, per Step 2. Three tests in `CollectStateTest.kt`:
- Two regressions that fail on `0bc430c`, one for each defect.
- One positive control.

Expected results on unmodified code were observed in the verification worktree:
`baseline=1 afterUnrelated=4` and `expected:<10> but was:<0>`. With the Step 3
code they were `baseline=1 afterUnrelated=1` and `10`.

## Done criteria

- [ ] `CollectStateTest` exists; before Step 3, 2 fail and 1 passes. After Step 3, all 3 pass.
- [ ] `collectState` no longer reads the whole `State<S>` in composition (`derivedStateOf` backs it)
- [ ] KDoc updated per Step 4
- [ ] `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` and `./gradlew build` exit 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:
- Step 2's regression tests pass against unmodified code. That would mean drift: someone already fixed it.
- Step 1 fails because `libs.compose.multiplatform.uiTest` does not resolve at the `compose-plugin` version. Report the resolution error. Do not pin another version without asking.
- The Step 3 code does not make both regressions pass, or it breaks the positive control.
- Plan 029 B5 is half-applied: `AnchorStateScopeImpl` is found in neither file, or in both.

## Maintenance notes

- Reviewer focus: confirm `remember(state)` is keyed on the `State` object, not the value, and that no composition-time read of `state.value` remains.
- Plan 001 is stale on two harness points (spec §6 Q1): the deprecated `runComposeUiTest` and the missing Main dispatcher. It also says `anchor-compose` has 4 files, but `AnchorConsumer.kt` has since landed. Plan 001 test 4 ("recomposition counting is out of scope") is how this bug stayed hidden. If 001 lands later, it should adopt this `desktopTest` set-up or move these tests into its `commonTest` harness.
- If a future `collectState` overload takes a `SnapshotMutationPolicy`, thread it into `derivedStateOf(policy) { ... }`.
- Tracker: this resolves the still-open half of #143 (closed 2026-01-23). See spec §7 for the #145 umbrella table.

## Execution results (2026-09-29)

- **Branch / PR**: `fix/025-collectstate-granular-recomposition` @ `b6542f5`, PR [#276](https://github.com/kioba/anchor/pull/276). It is stacked on `test/001-anchor-compose-harness` (#274, itself on #271).
- **Commits**:
  - `1e30213` 🧪 Add collectState recomposition and selector regression tests
  - `b6542f5` 🐛 Back collectState with derivedStateOf. This applies the Step 3 code verbatim, drops the `getValue` import, and adds the Step 4 KDoc.
- **Drift check**: `collectState` was unchanged on the base. #271 touched only `RememberAnchor.kt` (`PreviewAnchor`, `LocalAnchors`). 029 B5 has not landed.
- **Test evidence**:
  - Before the fix (`1e30213`), `CollectStateTest` failed 2 of 3: `the collectState caller recomposed for label-only changes expected:<1> but was:<4>` and `collectState returned a stale value after the selector changed expected:<10> but was:<0>`. The positive control passed.
  - After the fix, all 3 pass, and 5 of 5 `--rerun` runs passed.
  - `:anchor-compose:desktopTest` passes 13/13.
  - `:anchor-compose:compileKotlinIosSimulatorArm64` exits 0.
  - Full `./gradlew build`, iOS included, exits 0 (4m59s).
- **Deviations**:
  - Step 1 was skipped because #271 already added the desktopTest dependencies and aliases.
  - The tests reuse plan 001's `TestFixtures` (`TestState`/`testAnchor`) and `CompositionCounter`/`drainUiThread` instead of `ProbeState`/`ProbeAnchor`.
  - Plan 001's `RememberAnchorTest` test 4 (consumer-only count) moved into `CollectStateTest`, split in two:
    - label-only updates, which now count the caller and the consumer;
    - the positive control on a count change, where both counts are 2.
  - The branch name carries the `025-` prefix.
  - `plans/README.md` was not updated, because the operator maintains the index.
