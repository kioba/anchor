# Plan 022: Investigate-and-decide — resolve `anchor()` actions by ViewState type instead of nearest-provider + unchecked cast

> **Executor instructions**: This is an INVESTIGATE-THEN-ACT plan with a hard
> decision gate. Complete Phase A (investigation) and write the findings into
> this file under "Investigation results". Then STOP for maintainer review
> before executing Phase B. Do not perform Phase B in the same run unless the
> operator explicitly pre-authorized it *and* supplied answers to spec Q1
> (B1 or B4) and Q2 (missing provider: error or no-op). Follow every step and
> run every verification command. If anything in "STOP conditions" occurs,
> stop and report. When done (either phase), update the status row for this
> plan in `plans/README.md`, unless a reviewer dispatched you and told you
> they maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor-compose/ docs/compose.md`
> Expected benign drift, each of which has to be handled:
> - Plan 009 may have wrapped each overload's lambda in `remember(scope, block)`. Keep the memoization.
> - Plan 025 may have added `desktopTest` dependencies. Reuse them.
> - Plan 024 may have changed the `LocalSignals` wiring in `RememberAnchor.kt`. Merge around it.
> - Plan 029 may have moved files or added ABI dumps. Follow the new paths, and update the dumps in Phase B.
>
> Any change to `LocalAnchor` semantics or to the `anchor()` signatures is a STOP.

## Status

- **Priority**: P1
- **Effort**: M (A: S, B: M)
- **Risk**: MED. Public API change (binary-breaking), with composition-local plumbing.
- **Depends on**: none hard.
  - Harness: reuse plan 025 Step 1 (the `desktopTest` dependencies). If that plan has not landed, copy its Step 1 verbatim.
  - **Supersedes plan 009** if B1 is chosen: the new overloads include the `remember`. If B4 is chosen, 009 stays independent.
  - Coordinate the `RememberAnchor.kt` provider block with plan 024.
  - The CHANGELOG entry needs the `CHANGELOG.md` that plan 027 creates.
- **Category**: bug / api
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: [plans/specs/022-anchor-action-type-resolution.md](specs/022-anchor-action-type-resolution.md)

## Why this matters

`anchor()` claims "compile-time type safety" (`AnchorAction.kt:15`). What it
actually does is take the **nearest** `RememberAnchor`'s scope and cast blindly
(`(this as A)`, erased). The sample app nests anchors (Main → Counter/Config),
so a page that calls a parent action behaves in one of two ways. Both were
verified at `0bc430c`:

- `ClassCastException: IState cannot be cast to OState`. It goes to `defect` if one is configured, and otherwise escapes uncaught from `viewModelScope`, which crashes Android and aborts Kotlin/Native.
- **Silent misroute**: a `post {}`-only action emits its signal on the *inner* anchor's stream (`outerHandled=0 innerHandled=1`), with no error at all.

## Current state

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt:38-48`. The 1-, 2- and 3-arity overloads at `:81-94`, `:125-138` and `:154-167` follow the same pattern:

  ```kotlin
  @Composable
  public fun <A> anchor(
    block: suspend A.() -> Unit,
  ): () -> Unit
    where A : Anchor<out Effect, out ViewState, *> {
    val scope = LocalAnchor.current
    return {
      @Suppress("UNCHECKED_CAST")
      scope.execute { (this as A).block() }
    }
  }
  ```

- `LocalScope.kt:23-29`: `@PublishedApi internal val LocalAnchor: ProvidableCompositionLocal<AnchorScope<*, *>> = staticCompositionLocalOf { AnchorScope<Effect, ViewState> { _ -> /* No-op … preview/testing */ } }`
- `RememberAnchor.kt:155-171`: the ViewModel key defaults to `S::class.qualifiedName`, and the provider block is:

  ```kotlin
  CompositionLocalProvider(
    LocalSignals provides signalFlow,
    LocalAnchor provides anchorScope,
    content = { compositionScope.content() },
  )
  ```

- `PreviewAnchor` (`RememberAnchor.kt:95-102`) provides no `LocalAnchor`, so previews rely on the no-op default. `features/main/.../MainUi.kt:61-67` previews `MainUi`, whose `HomePage` calls `anchor(MainAnchor::clear)` (`HomePage.kt:56`).
- There are 12 call sites, all of them function references: `features/counter/.../CounterPage.kt:62,66`, `features/config/.../ConfigPage.kt:41`, `features/main/.../HomePage.kt:56,65,74,83,93,97` and `features/main/.../NavigationBarUi.kt:26,37,48`. `git grep -n "anchor<"` is empty. `MainSubscriptions.kt:32` `.anchor(...)` is the unrelated `Flow` extension in `SubscriptionDsl.kt`.
- Error routing: `ContainerViewModel.execute` (`ContainerViewModel.kt:33-42`) → `safeExecute` → `catchDefects`, which rethrows when no `defect` is set (`internal/ErrorHandling.kt:28-33`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Scratch worktree (Phase A) | `git worktree add ../anchor-145b origin/master` | created |
| New tests | `./gradlew :anchor-compose:desktopTest --tests 'dev.kioba.anchor.compose.NestedAnchorTest'` | see steps |
| iOS compile | `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` | exit 0 |
| Docs regen | `bash scripts/generate-llms-full.sh` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

If another Gradle process holds the build cache lock, add `--no-build-cache`.

## Scope

**Phase A** (scratch worktree only). The only in-repo change is appending "Investigation results" to this file.

**Phase B** (after GO):
- **In scope**:
  - `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/{AnchorAction.kt,LocalScope.kt,RememberAnchor.kt}`
  - `anchor-compose/src/desktopTest/kotlin/dev/kioba/anchor/compose/NestedAnchorTest.kt` (create)
  - `anchor-compose/build.gradle.kts` and `gradle/libs.versions.toml`, only if 025's Step 1 has not landed
  - `docs/compose.md` ("Dispatching Actions") and the regenerated `docs/llms-full.txt`
  - `CHANGELOG.md`, if it exists
  - ABI dump files, if they exist
- **Out of scope**: `AnchorConsumer.kt`, `LocalSignal.kt`/`HandleSignal` routing (spec Q4), `anchor/` core, and iOS.

## Git workflow

- Branch: `fix/anchor-action-type-resolution`
- Commits (gitmoji):
  - `🧪 Add nested-anchor dispatch regression tests`
  - `💥 Resolve anchor() actions by ViewState type` (B1), or `📝 Document anchor() nearest-provider binding` (B4)
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A — Investigation (always safe; scratch worktree)

**A1. Reproduce.** In the scratch worktree, apply plan 025 Step 1
(the dependencies). Add `NestedAnchorTest.kt` from the Test plan below and run it.

**Verify**:
- `outer reduce action…` FAILS, because `defect` holds a `ClassCastException`.
- `outer signal action…` FAILS, with `innerHandled=1` and `outerHandled=0`.
- `inner action still reaches…` PASSES.

If the first two pass, STOP (drift).

**A2. Inventory.** Record the output of these commands in Investigation results:
- `git grep -n -E "(^|[^.])\banchor\(" -- '*.kt'`
- `git grep -n "anchor<"`
- `grep -n "anchor(" README.md docs/*.md`

Flag any explicit type arguments and any lambda-literal usages. Those are the source breaks.

**A3. Prototype B1 in place.** In the scratch worktree:
- Replace all four overloads with the inline-reified form from spec §3.
- Add `LocalAnchors`.
- Make `RememberAnchor` provide the accumulated map. Keep `LocalAnchor`.

The prototype below was verified at `0bc430c` for arities 0 and 1, under the side-by-side name `anchorTyped`:

```kotlin
// LocalScope.kt
@PublishedApi
internal val LocalAnchors: ProvidableCompositionLocal<Map<KClass<*>, AnchorScope<*, *>>> =
  staticCompositionLocalOf { emptyMap() }

// RememberAnchor.kt, before CompositionLocalProvider
val parentAnchors = LocalAnchors.current
val anchors = remember(parentAnchors, anchorScope) { parentAnchors + (S::class to anchorScope) }
// …and add `LocalAnchors provides anchors,` to the provider list

// AnchorAction.kt (0-arity; 1-3 analogous)
@Composable
public inline fun <R : Effect, reified S : ViewState, Err : Any> anchor(
  noinline block: suspend Anchor<R, S, Err>.() -> Unit,
): () -> Unit {
  val scope =
    LocalAnchors.current[S::class]
      ?: error("anchor(): no enclosing RememberAnchor for ${S::class.simpleName}")
  return remember(scope, block) {
    {
      scope.execute {
        @Suppress("UNCHECKED_CAST")
        (this as Anchor<R, S, Err>).block()
      }
    }
  }
}
```

**Verify**:
- `NestedAnchorTest` is all green.
- `./gradlew build` exits 0 in the scratch worktree, so every sample call site infers.
- `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` exits 0.

Record any inference failure verbatim. Arity 2-3 with typealias references was not prototyped; confirm it here.

**A4. Preview behavior.** With `error(...)` on a miss, determine whether
`features/main` previews (`MainUi.kt:61-67` via `PreviewAnchor`) would throw.
Prototype the Q2 alternative: `PreviewAnchor` registers a no-op `AnchorScope`
under its `S::class` in `LocalAnchors`. Record which of the two options keeps
previews rendering.

**A5. Binary-compat check.** Decide whether hidden binary shims for the old
`<A>` overloads are feasible. They would collide on JVM signature with the new
inline ones, so check whether `@JvmName` on the new overloads resolves it in
common code. Recommend shims or no shims. The library is pre-1.0 (0.1.x).

**A6. Write "Investigation results"** in this file. Include:
- A1-A5 outcomes;
- a GO/NO-GO recommendation for B1;
- the recommended answer to Q2;
- a proposed CHANGELOG migration note.

**STOP.** Phase B requires the maintainer's GO naming B1 or B4 and the Q2 answer.

### Phase B — Execution (only after maintainer GO)

**B1 (test-first).** Add `NestedAnchorTest.kt` (Test plan) to the branch.
Add the `desktopTest` dependencies if they are missing. Add one more test for
the chosen Q2 behavior:
- (error) creating `anchor(OuterAnchor::bump)` outside any `RememberAnchor` fails with a message containing `OuterState`;
- (no-op) under `PreviewAnchor(OuterState())`, invoking it does nothing and does not throw.

**Verify**: `./gradlew :anchor-compose:desktopTest --tests 'dev.kioba.anchor.compose.NestedAnchorTest'`. The two nested tests fail as in A1, and the inner test passes.

**B2 (if B1 was chosen).** Implement A3 for all four arities in `AnchorAction.kt`, plus `LocalAnchors` and the `RememberAnchor` provisioning.
- Implement the Q2 behavior. If it is no-op, add a `PreviewAnchor` registration.
- Keep each overload's KDoc samples. Replace "providing compile-time type safety" with: "Resolves the nearest enclosing [RememberAnchor] whose ViewState is `S`; actions of an outer anchor called inside a nested one run on the outer anchor."
- Make sure `remember(scope, block)` is present. If plan 009 landed, it already is; otherwise it is added here, and plan 009 gets marked superseded.

**Verify**: `./gradlew :anchor-compose:desktopTest` is all green, and `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` exits 0.

**B2' (if B4 was chosen instead).**
- In all four KDocs, replace the "compile-time type safety" claim with the real rule: nearest `RememberAnchor`; a mismatched receiver raises `ClassCastException` via `defect`, or can silently misroute `post`/`emit`.
- Change the two nested tests to document the current behavior: expect the CCE and expect `innerHandled=1`.

**Verify**: `./gradlew :anchor-compose:desktopTest` exits 0.

**B3. Docs.**
- In `docs/compose.md` "Dispatching Actions", add a "Nested anchors" paragraph. It states the resolution rule, and under B4 it adds the hoisting workaround.
- Run `bash scripts/generate-llms-full.sh`.
- Add a CHANGELOG migration note if `CHANGELOG.md` exists. Under B1: the binary break and the explicit-type-argument source break.

**Verify**: `git diff docs/llms-full.txt` shows only the intended text.

**B4. Full gate.**

**Verify**: `./gradlew build` → exit 0. If ABI dumps exist, update them and confirm that the only change is the `anchor` overloads (plus `LocalAnchors` if `@PublishedApi` members are dumped).

## Test plan

Test-first. Create `anchor-compose/src/desktopTest/kotlin/dev/kioba/anchor/compose/NestedAnchorTest.kt`:

```kotlin
package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.EmptyEffect
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

data class OuterState(
  val n: Int = 0,
) : ViewState

data class InnerState(
  val s: String = "",
) : ViewState

typealias OuterAnchor = Anchor<EmptyEffect, OuterState, Nothing>

typealias InnerAnchor = Anchor<EmptyEffect, InnerState, Nothing>

data object GoHome : Signal

suspend fun OuterAnchor.bump() {
  reduce { copy(n = n + 1) }
}

suspend fun OuterAnchor.goHome() {
  post { GoHome }
}

suspend fun InnerAnchor.rename(s: String) {
  reduce { copy(s = s) }
}

class NestedProbe {
  val defect = AtomicReference<Throwable?>(null)
  val outerN = AtomicInteger(0)
  val innerS = AtomicReference("")
  val outerHandled = AtomicInteger(0)
  val innerHandled = AtomicInteger(0)
  var bump: (() -> Unit)? = null
  var goHome: (() -> Unit)? = null
  var rename: ((String) -> Unit)? = null
}

@Composable
fun NestedHarness(
  probe: NestedProbe,
  key: String,
) {
  RememberAnchor(
    scope = {
      create<EmptyEffect, OuterState, Nothing>(
        effectScope = { EmptyEffect },
        initialState = ::OuterState,
        defect = { probe.defect.set(it) },
      )
    },
    customKey = "$key-outer",
  ) {
    probe.outerN.set(state.n)
    HandleSignal<GoHome> { probe.outerHandled.incrementAndGet() }
    RememberAnchor(
      scope = {
        create<EmptyEffect, InnerState, Nothing>(
          effectScope = { EmptyEffect },
          initialState = ::InnerState,
          defect = { probe.defect.set(it) },
        )
      },
      customKey = "$key-inner",
    ) {
      probe.innerS.set(state.s)
      HandleSignal<GoHome> { probe.innerHandled.incrementAndGet() }
      probe.bump = anchor(OuterAnchor::bump)
      probe.goHome = anchor(OuterAnchor::goHome)
      probe.rename = anchor(InnerAnchor::rename)
    }
  }
}

@OptIn(ExperimentalTestApi::class)
class NestedAnchorTest {
  @Test
  fun `outer reduce action called inside a nested RememberAnchor updates the outer anchor`() =
    runComposeUiTest {
      val probe = NestedProbe()
      setContent { NestedHarness(probe, "reduce") }
      waitForIdle()
      probe.bump!!()
      waitUntil(timeoutMillis = 5_000) { probe.outerN.get() == 1 || probe.defect.get() != null }
      assertNull(probe.defect.get(), "outer action ran on the inner anchor")
      assertEquals(1, probe.outerN.get())
    }

  @Test
  fun `outer signal action called inside a nested RememberAnchor posts on the outer anchor`() =
    runComposeUiTest {
      val probe = NestedProbe()
      setContent { NestedHarness(probe, "signal") }
      waitForIdle()
      probe.goHome!!()
      waitUntil(timeoutMillis = 5_000) { probe.outerHandled.get() + probe.innerHandled.get() > 0 }
      waitForIdle()
      assertEquals(1, probe.outerHandled.get(), "signal not delivered to the outer anchor")
      assertEquals(0, probe.innerHandled.get(), "signal misrouted to the inner anchor")
    }

  @Test
  fun `inner action still reaches the inner anchor`() =
    runComposeUiTest {
      val probe = NestedProbe()
      setContent { NestedHarness(probe, "inner") }
      waitForIdle()
      probe.rename!!("x")
      waitUntil(timeoutMillis = 5_000) { probe.innerS.get() == "x" }
      assertNull(probe.defect.get())
    }
}
```

Expected results on `0bc430c`, from the verification run with equivalent fixtures:
- `outer reduce…`: FAILS with `ClassCastException: …InnerState cannot be cast to …OuterState`.
- `outer signal…`: FAILS with `outerHandled=0 innerHandled=1 defect=null`.
- `inner…`: PASSES.

After B1, all 3 pass, plus the Q2 test.

## Done criteria

Phase A:
- [ ] "Investigation results" appended: A1 failures reproduced, A2 inventory, A3 prototype outcome for arities 0-3 plus build/iOS, A4 preview finding, A5 shim recommendation, GO/NO-GO, and the Q2 recommendation
- [ ] No in-repo change other than this file (`git status`)

Phase B (post-GO):
- [ ] `NestedAnchorTest` passes (3 tests plus the Q2 test), and the B1 tests were observed failing before implementation
- [ ] KDoc on all four overloads states the real resolution rule, and `docs/compose.md` has the nested-anchors note, with `llms-full.txt` regenerated
- [ ] CHANGELOG note added (if the file exists), and ABI dumps updated (if they exist)
- [ ] `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` and `./gradlew build` exit 0
- [ ] `plans/README.md` status row updated, and plan 009 marked superseded if B1 landed its `remember`

## STOP conditions

- End of Phase A is a mandatory stop.
- A1: the nested tests pass on unmodified code. That is drift; report it.
- A3: any sample call site fails inference under B1. Report the call and the compiler error. Do not add explicit type arguments to samples to paper over it.
- B2: making the tests pass would require changing `AnchorScope`, `ContainerViewModel` or `anchor/` core.
- ABI dump diffs show changes beyond the `anchor` overloads and `LocalAnchors`.

## Maintenance notes

- Reviewer focus:
  - the `remember` keys `(parentAnchors, anchorScope)` in `RememberAnchor`;
  - the `remember(scope, block)` in each overload;
  - that `LocalAnchor` is still provided for `AnchorConsumer`.
- `LocalSignals` stays nearest-wins, so `HandleSignal` inside a nested subtree still cannot see outer signals (spec Q4). After B1, the outer `post {}` goes to the outer anchor, and only outer-scope `HandleSignal`s see it. Document that in B3.
- Relation to other plans:
  - Plan 009 (memoize callbacks) is absorbed by B1.
  - Plan 024 edits the same provider block. The second to land rebases.
  - Plan 027's CHANGELOG hosts the migration note.
- Tracker: this resolves #132's still-open substance. Closure of the umbrella #145 is covered in spec 025 §7.

## Investigation results

Run 2026-09-29 on `origin/master` = `0bc430c`, in worktree branch
`fix/022-anchor-action-type-resolution`. The operator pre-authorized Phase B
(maintainer GO: **B1**; Q2: `error(...)` in `RememberAnchor` trees plus a
no-op `PreviewAnchor` entry for its `S`; Q3: nearest wins; Q4 out of scope).
Phase A was still run first and did not contradict the spec.

**Drift check.** `git diff --stat 0bc430c..HEAD -- anchor-compose/ docs/compose.md`
printed nothing: `origin/master` is still `0bc430c`. Plans 009, 024, 025 and
029 have not landed, so there are no `remember` wrappers, `desktopTest`
dependencies, `LocalSignals` changes or ABI dumps to merge around. There is no
`CHANGELOG.md` (plan 027 has not landed).

**A1 — reproduced.** I applied plan 025 Step 1 verbatim and added
`NestedAnchorTest.kt` from the Test plan. Running
`./gradlew :anchor-compose:desktopTest --tests 'dev.kioba.anchor.compose.NestedAnchorTest'`
on unmodified code gave:
- `outer reduce…`: FAILED, with `defect` = `java.lang.ClassCastException: class dev.kioba.anchor.compose.InnerState cannot be cast to class dev.kioba.anchor.compose.OuterState`.
- `outer signal…`: FAILED, "signal not delivered to the outer anchor expected:<1> but was:<0>".
- `inner action still reaches…`: PASSED.
- Added for B1 and run in the same pass. `anchor outside any RememberAnchor fails naming the missing ViewState` FAILED ("Expected an exception of class java.lang.IllegalStateException to be thrown, but was completed successfully", which is today's silent no-op). `anchor under PreviewAnchor is a no-op` PASSED (today's no-op default).
- Finding for plan 009: a referential-stability probe (`anchor returns the same callback across recompositions`) already PASSES on `0bc430c`. The Compose compiler's strong-skipping lambda memoization remembers the returned lambda against its captures `(scope, block)`, so plan 009's premise ("FAILS today") is stale on the current toolchain.

**A2 — inventory.**
- `git grep -n -E "(^|[^.])\banchor\(" -- '*.kt'` printed nothing, because macOS `git grep -E` does not support `\b`. The portable equivalent, `git grep -n -E "(^|[^.a-zA-Z0-9_])anchor\(" -- '*.kt'`, lists the 4 declarations, KDoc mentions, and exactly the 12 sample call sites from "Current state". All are function references with arities 0 and 1 (`ConfigAnchor::updateText` is the only 1-arity one), and `NavigationBarUi.kt:37` uses the named argument `anchor(block = MainAnchor::selectHome)`, so the parameter name `block` must stay.
- `git grep -n "anchor<"`: only `androidApp/src/androidMain/res/values/strings.xml:2` (`<string name="app_name">anchor</string>`, unrelated). **No explicit type arguments.**
- `grep -n "anchor(" README.md docs/*.md`: `README.md:76,77`, `docs/compose.md:31,91,96,99,199,200`, `docs/index.md:62`, `docs/examples.md:74,77,80` are all function references. `docs/api.md:58-64` lists the signatures as `anchor(suspend A.() -> Unit)` etc. It is still accurate as a description of the call form, so I left it alone (out of scope).
- **No lambda-literal usages anywhere. Source breaks in repo: none.**

**A3 — B1 prototype (all four arities).** Implemented in place as spec §3.
All four overloads became `inline` with `reified S` and a `noinline block`,
plus `remember(scope, block)`. I also added `@PublishedApi internal val LocalAnchors`,
and `RememberAnchor` now provides `parent + (S::class to anchorScope)` while
still providing `LocalAnchor`.
- `NestedAnchorTest`: all green (3 nested tests plus the Q2 tests plus the stability probe).
- Arity 2 and 3 with typealias references (`InnerAnchor::join2` taking `(String, Int)`, and `anchor(block = InnerAnchor::join3)` taking `(String, Int, Boolean)`) inferred `R`/`S`/`Err`/`I`/`O`/`T` and executed on the right anchor. I checked this with a scratch desktop test that was not committed. No inference failures.
- `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64`: exit 0.
- `./gradlew build --no-build-cache`: exit 0 (637 tasks, including iOS simulator tests, framework links and Android lint). All 12 sample call sites compile unchanged (`git diff 0bc430c -- features/ androidApp/` is empty).

**A4 — previews.** With `error(...)` on a miss and no registration,
`MainPreview` (`MainUi.kt:61-67`) would throw at composition. `MainUi` →
`HomePage` → `anchor(MainAnchor::clear)` finds no `MainViewState` entry in
`LocalAnchors`, because `PreviewAnchor` provides none. The desktop test
confirms that outside a provider `anchor()` throws `IllegalStateException`
naming the state. With the Q2 alternative, `PreviewAnchor` registers a no-op
`AnchorScope` under its `S::class`, the lookup succeeds, and invoking the
callback does nothing: `anchor under PreviewAnchor is a no-op` passes. **Only
the registration keeps previews rendering.**
- Implementation note: `PreviewAnchor` was `public fun <S : ViewState>`, which is non-inline, so its `S` is not available at runtime. The only way to register "its `S`" is to make it `inline` with `reified S` and `crossinline content`, like `RememberAnchor`. The alternative is `state::class`, which keeps `PreviewAnchor`'s ABI but keys a sealed or open ViewState under the runtime subclass (e.g. `PreviewAnchor<MainState>(MainState.Loading)` would register `Loading`), so the preview would throw. I chose reified. Call sites stay source-compatible (the sample's `PreviewAnchor(state) { MainUi() }` compiles unchanged), but `PreviewAnchor`'s JVM method now needs inlining too, so it joins the binary break. Generic wrappers of `PreviewAnchor` with a non-reified `S` no longer compile.
- `PreviewAnchor` also accumulates the parent map, so nesting `PreviewAnchor(MainViewState) { PreviewAnchor(CounterState) { … } }` previews a page that calls both anchors' actions. This is documented in the `PreviewAnchor` KDoc and in `docs/compose.md`.

**A5 — binary shims.** They are feasible but not recommended.
- The old `<A>` overloads and the new inline ones compile to the same JVM descriptor (e.g. `AnchorActionKt.anchor(Function2, Composer, int): Function0`). A hidden shim, `@Deprecated(level = HIDDEN)`, keeping the old `<A>` body would clash ("Platform declaration clash") unless the new overloads take a `@JvmName`. I verified this. The hidden 0-arity `<A>` shim alone fails `:anchor-compose:compileKotlinDesktop` with "Platform declaration clash … anchor(Lkotlin/jvm/functions/Function2;Landroidx/compose/runtime/Composer;I)Lkotlin/jvm/functions/Function0;". Adding `@kotlin.jvm.JvmName("anchorByState")` to the new overload in `commonMain` compiles for desktop, Android and iosSimulatorArm64. `javap` then shows `anchorByState(Function2, Composer, int)` next to the shim's `anchor(Function2, Composer, int)`, and the desktop tests still pass. `PreviewAnchor` would need the same treatment.
- Without shims, old binaries still link, because the descriptor is unchanged. At runtime they reach the reified body, which throws `UnsupportedOperationException` (reified operation marker) instead of dispatching. That is a hard failure, not a silent misroute.
- **Recommendation: no shims.** The library is 0.1.x. A shim would keep the buggy nearest-provider dispatch alive for stale binaries and pin a JVM-only `@JvmName` on the new public API forever. Consumers recompile against a new version anyway. Document the break in the release notes instead.

**GO/NO-GO:** GO for B1. All A1–A4 verifications pass, no sample call site
needs changes, and the maintainer GO was already given.

**Q2 recommendation (adopted):** `error(...)` when no enclosing
`RememberAnchor`/`PreviewAnchor` provides `S`. The message is
`anchor(): no enclosing RememberAnchor provides <State>`. `PreviewAnchor`
registers a no-op entry for its `S`.

**Proposed CHANGELOG migration note** (for plan 027's `CHANGELOG.md`):

> **anchor-compose — BREAKING: `anchor()` now dispatches by ViewState type.**
> `anchor(X::action)` runs on the nearest enclosing `RememberAnchor` whose
> ViewState matches the action's receiver `Anchor<R, S, Err>`, instead of on
> the nearest `RememberAnchor` regardless of type. Outer-anchor actions called
> inside a nested `RememberAnchor` used to throw `ClassCastException` or
> silently post signals to the inner anchor. They now run on the outer anchor.
> - *Binary*: the four `anchor()` overloads and `PreviewAnchor` are now
>   `inline` with a reified `S`. Recompile code that calls them. Stale
>   binaries fail with `UnsupportedOperationException`.
> - *Source*: function references (`anchor(CounterAnchor::increment)`) are
>   unchanged. Explicit type arguments (`anchor<CounterAnchor>(…)`), lambdas
>   without an inferable `Anchor<R, S, Err>` receiver, and generic wrappers
>   with a non-reified `S` no longer compile.
> - *Behavior*: calling `anchor()` with no enclosing `RememberAnchor` for its
>   ViewState now throws `IllegalStateException` instead of doing nothing.
>   Inside `PreviewAnchor(state)`, actions on that state's anchor are still
>   no-ops. Nest a `PreviewAnchor` per ViewState to preview content that calls
>   an outer anchor's actions.

## Execution results (2026-09-29)

- **Branch / PR**: `fix/022-anchor-action-type-resolution` → https://github.com/kioba/anchor/pull/271 (base `master`, from `origin/master` `0bc430c`). The PR's CI is expected to fail at "Setup Android SDK" until #268 lands.
- **Commits**:
  - `ad115ec` 🧪 Add nested-anchor dispatch regression tests (includes the plan 025 Step 1 dependencies)
  - `01eb6a6` 💥 Resolve anchor() actions by ViewState type
  - `2651e8f` 📝 Document nested-anchor action resolution
- **Implemented**: B1 (all four arities with `remember(scope, block)`), `LocalAnchors`, `RememberAnchor` provisioning (`LocalAnchor` kept), Q2 error on a miss, and the `PreviewAnchor` no-op registration. KDoc states the resolution rule. The `docs/compose.md` "Nested anchors" note and preview sentence are updated, and `docs/llms-full.txt` is regenerated.
- **Test evidence**:
  - `NestedAnchorTest` on `ad115ec` alone: `outer reduce…` FAIL (CCE in `defect`), `outer signal…` FAIL (outerHandled 0), and Q2 error test FAIL (no exception). `inner…`, preview no-op and stability tests PASS.
  - After the implementation, `./gradlew :anchor-compose:desktopTest` is 6/6 green. `./gradlew :anchor-compose:compileKotlinIosSimulatorArm64` exits 0, and `./gradlew build --no-build-cache` exits 0 (637 tasks).
- **Deviations**:
  - `PreviewAnchor` became `inline` with a `reified S` so that it can register "its `S`" (see A4). It is part of the binary break.
  - Two tests were added beyond "3 + Q2": both Q2 halves and a referential-stability test.
  - The gate ran with `--no-build-cache` because the disk was full: the first build failed with ENOSPC in an iOS link.
  - No `CHANGELOG.md` exists, so the migration note is in the PR body. There are no ABI dumps to update.
- **Plan 009**: absorbed. Callbacks are referentially stable (asserted by the test). The same test also passes on `0bc430c` because of Compose strong-skipping lambda memoization, so 009's premise was stale. Mark 009 superseded by 022 once #271 merges.
- `plans/README.md` was not edited, because the dispatching reviewer maintains the index.
