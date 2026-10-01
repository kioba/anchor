# Plan 028: Documentation overhaul — fix the wrong statements, then write the threading, lifecycle, guarantees, best-practice and migration guides

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- README.md AGENTS.md CLAUDE.md docs/ mkdocs.yml scripts/generate-llms-full.sh anchor/src anchor-compose/src anchor-test/src`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition. **Expected drift** that is *not* a
> STOP: the landing of plans 002, 004, 006, 012, the #266 plan, 025 or
> 023. For those, follow the reconciliation table in Step 0 instead.

## Status

- **Priority**: P2
- **Effort**: L. About 5 new or rewritten pages, 2 test files and a script change. Split into 3 PRs, see Git workflow.
- **Risk**: LOW. Docs, KDoc comments and test-only code; no runtime change. Phase B needs a maintainer decision.
- **Depends on**: none for Phase A. Phase B1 depends on the #266 plan (`plans/024-init-signal-delivery.md`) and plan 002 landing. Phase B2 depends on the maintainer's answer to spec Q2.
- **Category**: docs
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: [`plans/specs/028-docs-overhaul.md`](specs/028-docs-overhaul.md)
- **Issue**: https://github.com/kioba/anchor/issues/140
- **Supersedes**: plan 012 (absorbed as Step 2a–2b). **Overlaps**: plan 006 steps 3–4 (this plan writes the `docs/testing.md` half).

## Why this matters

Issue #140 asks for documentation of the threading model, lifecycle, State/Signal/Event guarantees, memory, testing and migration. Verified against `0bc430c`:

- **Six of the fourteen topics are missing entirely**: thread safety, state-update guarantees, signal delivery, memory, migration and best practices.
- **Five are partial**: when to use Signal vs State, the Effect scope, Event vs Signal, `cancellable`, and lifecycle. The concurrency model gets a single sentence.
- **Thirteen statements are wrong**, including:
  - the testing page's `assertEffect` description (following it produces a failing test);
  - the only error-handling example in Core Concepts, which swallows cancellation and domain errors;
  - the "granular recomposition" headline feature (split into 025).

Wrong docs are worse than missing docs. A state-management library's users need to know where their code runs and what is guaranteed. The spec's §1.2–§1.4 has the full matrix and the probe evidence.

## Current state

All paths are relative to the repo root; line numbers are at `0bc430c`.

- The nav has 7 pages (`mkdocs.yml` `nav:`): `index`, `concepts`, `compose`, `errors`, `testing`, `api` and `examples`. `docs/documents.md` is orphaned mkdocs boilerplate.
- `scripts/generate-llms-full.sh` hard-codes the page list and the per-page link rewrites:

  ```bash
      -e 's/](concepts\.md)/](https:\/\/kioba.github.io\/anchor\/concepts\/)/g' \
      -e 's/](api\.md)/](https:\/\/kioba.github.io\/anchor\/api\/)/g' \
  ...
  DOCS=(
    "docs/index.md"
    "docs/concepts.md"
    "docs/compose.md"
    "docs/errors.md"
    "docs/testing.md"
    "docs/api.md"
    "docs/examples.md"
  )
  ```

  CI `llms_check` (`.github/workflows/pr_check.yml:44-62`) fails if `docs/llms-full.txt` differs from the script output. CI `version_check` (`pr_check.yml:10-40`) requires README, `mkdocs.yml` and `CLAUDE.md` to share one version string. **Do not touch version strings.**
- W3, `docs/concepts.md:56-66`:

  ```kotlin
  suspend fun MyAnchor.loadData() {
      reduce { copy(isLoading = true) }
      try {
          val result = effect { repository.fetchData() }
          reduce { copy(isLoading = false, items = result) }
      } catch (e: Exception) {
          reduce { copy(isLoading = false, error = e.message) }
      }
  }
  ```

  `RaisedException` extends `CancellationException` (`anchor/src/commonMain/kotlin/dev/kioba/anchor/AnchorExceptions.kt:16-19`), so this catch swallows cancellation, `raise` and `orDie`.
- W4, `docs/testing.md:122-130`:

  ```markdown
  ### `assertEffect`

  Verifies an effect block was executed:
  ```

  The reality is that `AnchorTestRuntime.effect` records nothing (`anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorTestRuntime.kt:58-62`), while `assertEvents` starts with `assertEquals(expectedActions.size, actualActions.size)` (`anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/scopes/AnchorTestScope.kt:157`).
- W5, `docs/testing.md:13-14`: "`anchor-test` transitively includes the core `anchor` module and `kotlinx-coroutines-test`." In fact `anchor-test/build.gradle.kts:11` has `implementation(libs.kotlin.coroutinesTest)`, and the published POM gives it `<scope>runtime</scope>`.
- W12, `docs/errors.md:41`: "Omitting `onDomainError` means an unhandled `raise()` crashes the coroutine. Omitting `defect` lets unexpected exceptions propagate normally." Behind it, `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt:33-53`:

  ```kotlin
  override fun execute(
    block: suspend Anchor<R, S, *>.() -> Unit,
  ) {
    viewModelScope.launch(Dispatchers.Default) {
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        @Suppress("UNCHECKED_CAST")
        anchor.block()
      }
    }
  }

  init {
    viewModelScope.launch(Dispatchers.Default) {
      safeExecute(anchor, anchor.onDomainError, anchor.defect) {
        anchor.consumeInitial()
        with(anchor) {
          subscribe()
        }
      }
    }
  }
  ```

- W6, W7, `docs/api.md:37` "## Compose API (Android)" and `docs/api.md:58-64` list three `anchor()` overloads. There are four (`anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt:38-167`).
- W9, the KDoc samples at `AnchorAction.kt:26,65,115` and `AnchorConsumer.kt:20` use `RememberAnchor(scope = { counterAnchor() }) { state ->`. But `content` is `crossinline content: @Composable AnchorStateScope<S>.() -> Unit` (`anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt:153`).
- W10, `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:53-56`:

  ```kotlin
  /**
   * Event emitted when the Anchor is created.
   */
  public object Created : Event
  ```

  In reality it is emitted to each handler on subscription: `AnchorRuntime.kt:85-88` `_emitter.asSharedFlow().onSubscription { emit(Created) }`.
- W11, `anchor/src/commonMain/kotlin/dev/kioba/anchor/RememberAnchorScope.kt:19`: "@param init An optional initialization block executed once when the Anchor is created."
- Runtime facts the new pages describe:
  - `AnchorRuntime.kt:59-60` `MutableSharedFlow(extraBufferCapacity = 64)` (signals).
  - `AnchorRuntime.kt:64` `MutableSharedFlow()` (events, rendezvous).
  - `AnchorRuntime.kt:79` `internal val effect = effectScope()`.
  - `AnchorRuntime.kt:134-137` `reduce` = `_viewState.update(reducer)`.
  - `AnchorRuntime.kt:177-220` `cancellable`.
  - `Anchor.kt:196-199` `effect(coroutineContext = Dispatchers.IO, ...)`.
  - `RememberAnchor.kt:155-161` key and `viewModel(...)`.
  - `SubscriptionDsl.kt:46-53` `.anchor {}` = `onEach { safeExecute { action } }`.
- `anchor-test` ignores `init` and `subscriptions`. `AnchorTestScope.kt:105-118` builds the `AnchorTestRuntime` from the effect scope, initial state and handlers only.
- Baseline docs build (spec §1.1): `mkdocs build` exits 0 with exactly one `WARNING` (`The "site_url" option is not set...`). `--strict` fails on it.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Docs toolchain (once, outside the repo) | `python3 -m venv "$TMPDIR/anchor-docs-venv" && "$TMPDIR/anchor-docs-venv/bin/pip" install mkdocs-material mkdocs-macros-plugin pillow cairosvg` | exit 0 (same packages as `.github/workflows/publish_docs.yml`) |
| Docs build | `"$TMPDIR/anchor-docs-venv/bin/mkdocs" build -d "$TMPDIR/anchor-site" 2>&1 \| grep -E '^(WARNING\|ERROR)'` | exactly one line: the `site_url` warning |
| llms regen | `bash scripts/generate-llms-full.sh` | exit 0 |
| llms idempotency | run the regen twice; `git diff --stat docs/llms-full.txt` after the 2nd run equals after the 1st | no further change |
| No relative links in llms | `grep -nE '\]\([a-z0-9-]+\.md' docs/llms-full.txt` | no output |
| Pinning tests (core) | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.DocumentedBehaviorTest'` | exit 0 |
| Pinning tests (test DSL) | `./gradlew :anchor-test:desktopTest --tests 'dev.kioba.anchor.test.DocumentedLimitationsTest'` | exit 0 |
| Native run of pinning tests (macOS) | `./gradlew :anchor:iosSimulatorArm64Test :anchor-test:iosSimulatorArm64Test` | exit 0 (no signal-6 abort) |
| Sample snippets compile | `./gradlew :features:main:desktopTest :features:counter:desktopTest :features:config:desktopTest` | exit 0 |
| Full build | `./gradlew build` | exit 0 |
| Version guard | `git diff 0bc430c -- README.md mkdocs.yml CLAUDE.md gradle.properties \| grep -E '^[-+].*0\.1\.[0-9]'` | no output |

## Scope

**In scope**:
- `docs/index.md`, `docs/concepts.md`, `docs/compose.md`, `docs/errors.md`, `docs/testing.md`, `docs/api.md`, `docs/examples.md`
- New: `docs/threading.md`, `docs/lifecycle.md`, `docs/guarantees.md`, `docs/best-practices.md`, `docs/migration.md` (Phase B)
- Delete: `docs/documents.md`
- `docs/llms.txt`, `docs/llms-full.txt` (regenerated, never hand-edited), `scripts/generate-llms-full.sh`, `mkdocs.yml` (the `nav:` block only)
- `README.md` (the intro sentence, the Quick Start actions and the "Learn More" links), `CLAUDE.md:202` (the one sentence from 012), and `AGENTS.md` lines repeating W1, W2 or W8
- KDoc comments only in:
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt`
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/RememberAnchorScope.kt`
  - `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/*.kt`
  - `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt`
  - `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorConsumer.kt`
  - `anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/**/*.kt`
- New test files (test-only):
  - `anchor/src/commonTest/kotlin/dev/kioba/anchor/DocumentedBehaviorTest.kt`
  - `anchor-test/src/commonTest/kotlin/dev/kioba/anchor/test/DocumentedLimitationsTest.kt`
  - optionally a snippet test in `features/main/src/commonTest/...` (Step 8)

**Out of scope**:
- Any non-comment change under `*/src/*Main/`. Behaviour fixes belong to 025, 023, the #266 plan, and plans 002, 004 and 005.
- Version strings anywhere.
- `mkdocs.yml` outside `nav:` (for example `site_url`). That is spec Q5 and needs the maintainer's answer.
- Dokka (plan 013).
- The `AnchorTestRuntime`/`runAnchorTest` divergence KDoc. Plan 006 owns it. If 006 has not landed, add only the `runAnchorTest`/`runAnchorSequenceTest` KDoc in Step 8, and nothing on `AnchorTestRuntime`.
- Changing or deprecating `assertEffect` (spec Q3).

## Git workflow

- Branches, one PR each:
  - `docs/140-accuracy-fixes` (Steps 0–3)
  - `docs/140-guides` (Steps 4–10)
  - `docs/140-guides-phase-b` (Steps 11–13, after GO)
- Commit style: gitmoji, for example:
  - `🧪 Pin documented runtime behaviour with tests`
  - `📝 Fix wrong statements in docs and KDoc`
  - `🔧 Generate llms-full.txt from every nav page`
  - `📝 Add threading, lifecycle and guarantees guides`
- End each commit with the session attribution lines your operator requires.
- Do NOT push or open a PR unless the operator instructed it.
- Each PR description carries the **claims ledger** (spec §3.2 V1): a table `claim | evidence (file:line@sha, test FQN, or URL+version)`.

## Steps

### Phase A — document current behaviour (no design decision needed)

### Step 0: Drift check, reconciliation, baseline

1. Run the drift check. Run `git log --oneline 0bc430c..HEAD` and read the `plans/README.md` status rows. Fill in this table in your notes and follow it:

   | If this landed | Then |
   |---|---|
   | plan 012 | Skip Step 2a–2b, but re-run their greps |
   | plan 006 steps 3–4 | Step 8 extends 006's `docs/testing.md` section instead of creating it |
   | #266 plan + 002 | Do Step 11 (B1) as part of Step 6 and skip the interim admonition |
   | plan 004 | Pinning test 4 must be written for the new behaviour (init events delivered). Adjust the lifecycle and guarantees text |
   | 025 | W8 needs no docs change. Keep the "granular" claims and describe the equality semantics |
   | 023 | Pinning test 6 must assert the new emit semantics. Drop the "Known issue" admonition |

2. Install the docs toolchain (Commands table) and record the baseline warnings.
3. Run the llms regen twice on a clean tree.

**Verify**:
- The docs build prints exactly one `WARNING` line, the `site_url` one. If it prints more, record them as the new baseline and note it in the PR.
- `git status --short docs/` is empty after two regens.

### Step 1: Pinning tests first (they must PASS on unmodified main code)

These tests pin the behaviour the new pages will state (spec §3.2 V2). Each test's KDoc names the doc page and section it backs. Use the style of `anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableTest.kt`: backticked names, `runBlocking`, `withTimeout` guards, and the fixtures `TestState`/`TestError`/`EmptyEffect` that already exist in that source set.

ViewModel harness (common code; `containerViewModelFactory` and the `ContainerViewModel` constructor are `@PublishedApi internal`, so they are visible from `anchor`'s own tests):

```kotlin
private fun <R : Effect, S : ViewState, Err : Any> model(
  runtime: AnchorRuntime<R, S, Err>,
): Pair<ViewModelStore, ContainerViewModel<R, S, Err>> {
  val store = ViewModelStore()
  val owner = object : ViewModelStoreOwner { override val viewModelStore = store }
  @Suppress("UNCHECKED_CAST")
  val vm = ViewModelProvider.create(owner, containerViewModelFactory { runtime })[ContainerViewModel::class]
    as ContainerViewModel<R, S, Err>
  return store to vm
}
```

Always `store.clear()` in `finally`, so that no coroutine outlives the test. That matters on Native, where a stray failure under a SupervisorJob aborts the process.

`anchor/src/commonTest/kotlin/dev/kioba/anchor/DocumentedBehaviorTest.kt`:

1. **`threading - actions dispatched with execute run concurrently`**: action A awaits a `CompletableDeferred` gate; action B completes a deferred. Assert that B completes while the gate is still closed (`withTimeout(2_000)`). This is spec probe P3.
2. **`threading - execute runs on Dispatchers Default and effect switches to Dispatchers IO`**: inside the action, capture `currentCoroutineContext()[ContinuationInterceptor]`, and do the same inside `effect { }`. Assert they equal `Dispatchers.Default` and `Dispatchers.IO` respectively. `Dispatchers.IO` needs `import kotlinx.coroutines.IO` in common code, as `Anchor.kt:4` does.
3. **`cancellable - a superseded caller resumes and runs the code after cancellable`**: spec probe P2. Assert the list equals `listOf("first-after", "second-after")`.
4. **`lifecycle - init completes before connect handlers attach so events emitted in init are dropped`**: spec probe P5. Assert that the handler saw exactly `[Created]`. The KDoc says "flips when plan 004 lands".
5. **`events - every connect handler receives Created once when it subscribes`**: two `connect<Event>` handlers. Each sees `Created` exactly once.
6. **`events - emit waits while a handler is still busy with the previous event`**: spec probe P6. Assert `withTimeoutOrNull(300) { emit }` is `null`, then open the gate and assert that the next emit returns. The KDoc says "flips when 023 lands".
7. **`lifecycle - clearing the store cancels cancellable jobs and empties the job map`**: spec probe P4.
8. **`lifecycle - a domain error handled in init leaves subscriptions detached`**:
   - Set `init = { raise(TestError.NotFound) }` and `onDomainError = { handled++ }`, with one `connect<Event>` subscription.
   - After `handled == 1`, wait 200 ms and assert `runtime._emitter.subscriptionCount.value == 0`.
   - Source: the #266 spec §1.8.
   - The KDoc says this is a known defect. Name plan 004 or the issue the maintainer opens for it.

`anchor-test/src/commonTest/kotlin/dev/kioba/anchor/test/DocumentedLimitationsTest.kt`. The fixtures are local and private, like `EffectTest.kt`.

1. **`testing - runAnchorTest does not run init`**: the factory has `init = { reduce { copy(fromInit = true) } }`, the action is `on("noop") { }`, and `verify("") {}` passes.
2. **`testing - runAnchorTest does not run subscriptions`**: a subscription that `error(...)`s on `ProbeEvt`; the action emits `ProbeEvt`; `verify { assertEvent { ProbeEvt } }` passes.
3. **`testing - assertEffect fails verification when the other assertions match one to one`**: `assertFails { runAnchorTest(...) { ... verify { assertEffect { fetch() }; assertState { ... } } } }`. This is spec probe T1.
4. **`testing - verify needs one assertion per recorded action`**: the action does 2 `reduce`s and `verify` has 1 `assertState`. `assertFails`.
5. **`testing - a recorded reducer is invoked more than once`**: the reducer increments an external counter. After the test, assert that the counter is `>= 2`.
6. **`testing - cancellable runs inline and never cancels a previous block`**: two `cancellable("k") { reduce }` calls in one action. Both reductions are asserted.
7. **`testing - delay inside an action does not wait in real time`**: the action runs `delay(60_000)`. Measure `TimeSource.Monotonic.markNow()` around `runAnchorTest` and assert that it took `< 10.seconds`.

**Verify**: both pinning-test commands from the Commands table exit 0 on **unmodified** main code, and so does the Native command. Every test passing means every planned sentence is backed. A failure is STOP condition 1.

### Step 2: Fix the wrong statements (W1–W7, W9–W12) and stale text

- **2a (W1, from 012)**:
  - `README.md:7`: replace "Context Receivers and SAM conversions" with "receiver-based extension functions and SAM conversions".
  - `docs/index.md:6`: replace "built on Kotlin's Context receivers" with "built on receiver-based extension functions".
  - `docs/index.md:17`: rename the bullet to "**Receiver-based DSL**".
  - `CLAUDE.md:202`: reword so it no longer refers to a README error.
  - Fix `AGENTS.md` too if it repeats the claim.
- **2b (W2, from 012)**: prefix every action definition in `README.md`, `docs/index.md` and `docs/examples.md` with `suspend`. Find them with `grep -rnE '^\s*fun [A-Z][A-Za-z]*Anchor\.' README.md docs/*.md`. Do **not** touch factory functions such as `fun RememberAnchorScope.counterAnchor()`.
- **2c (W3)**: rewrite the `concepts.md` "Actions" example:
  - Let unexpected exceptions reach `defect`, and model expected failures with `raise`/`ensure` (link to `errors.md`).
  - If a local `catch` is shown at all, it must rethrow `CancellationException` first.
  - Add one sentence explaining why: `RaisedException` is a `CancellationException`.
  - Put the final snippet in a compiled test (Step 8's snippet file) per V3.
- **2d (W4)**: rewrite `testing.md` "assertEffect":
  - Effect calls are not recorded, so `assertEffect` cannot verify that an effect ran and makes `verify` fail (link to Step 8's "How matching works").
  - Verify effects through the state or signals they produce, or through a fake Effect that counts calls, for example `ProbeFx.calls`.
  - Backed by DocumentedLimitationsTest 3.
- **2e (W5)**: `testing.md:13-14`: `anchor-test` brings `anchor` transitively, **but** add `kotlinx-coroutines-test` yourself if your tests use it directly.
- **2f (W6, W7)**: `api.md`: "## Compose API" (not Android-only), and list all four `anchor()` overloads.
- **2g (W9, W10, W11, KDoc)**:
  - Change `{ state ->` to `{` in the four samples. Where the sample body used `state`, keep using `state`: `state` is a property of the `AnchorStateScope` receiver.
  - `Created`: "Delivered to each `connect` handler when it subscribes. Subscriptions attach after `init` completes."
  - `create(init = ...)`: "Runs once, when the owning `ContainerViewModel` is created, before subscriptions attach. It is not run by `runAnchorTest`."
- **2h (W12)**: rewrite `errors.md:41`:
  - Without `onDomainError`, `raise()` stops the action silently (it is a cancellation).
  - Without `defect`, a non-fatal exception reaches the platform's uncaught-exception handler: Android terminates the app, and iOS/Kotlin/Native aborts the process.
  - Cite kotlinx `CoroutineExceptionHandler` ("Platform-specific last-resort handling") in the ledger.
- **2i**: `docs/index.md:14`: "Supports Android, iOS and Desktop (JVM)."
- **2j**: `git rm docs/documents.md`.
- **2k (W13)**: `compose.md:123`: add one sentence linking to `guarantees.md#signals` (the anchor is created in Step 6; until then link to the page).

**Verify**:
- `grep -rni "context receiver" README.md docs/*.md docs/llms.txt AGENTS.md` prints nothing.
- The Step 2b grep shows only `suspend fun` lines.
- `grep -n "{ state ->" anchor-compose/src anchor/src -r` prints nothing.
- `grep -n "Compose API (Android)" docs/api.md` prints nothing.
- `./gradlew :anchor:compileKotlinDesktop :anchor-compose:compileKotlinDesktop` exits 0 (KDoc only).

### Step 3: Make the llms generator follow the nav

In `scripts/generate-llms-full.sh`:

1. Replace the 6 per-page `-e 's/](X\.md)/...'` rewrites with one extended-regex stage (`sed -E`, which works with both BSD and GNU sed). The stage must rewrite:
   - `](index.md)` → `](https://kioba.github.io/anchor/)`
   - `](<slug>.md)` → `](https://kioba.github.io/anchor/<slug>/)`
   - `](<slug>.md#frag)` → `](https://kioba.github.io/anchor/<slug>/#frag)`
2. Keep `DOCS=(...)` explicit, but add a guard. Extract every `*.md` from the `nav:` block of `mkdocs.yml` (`sed -n '/^nav:/,$p' mkdocs.yml | grep -oE '[a-z0-9-]+\.md'`). If any nav page is missing from `DOCS`, or `DOCS` differs from nav order, `exit 1` with a message.
3. Leave the header text as it is, including the iosX64 line (spec Q4).

**Verify**:
- Before any new page exists, `bash scripts/generate-llms-full.sh && git diff --exit-code docs/llms-full.txt` exits 0. The generic rewrite is byte-identical for today's pages.
- Temporarily add a bogus `- 'X': x.md` to the nav, and the script exits 1. Revert it.
- The "No relative links" command prints nothing.

**PR1 ends here.** Regenerate llms-full, run the docs build, run `./gradlew build`, and open PR1 if instructed.

### Step 4: `concepts.md` — the chooser table, Effect, cancellable, Events

Add the spec §3.1 `concepts.md` content:

- Table "Choosing between State, Signal, Event and Effect". Its columns are *What it is*, *Who observes it*, *Kept when nobody observes?*, *Use it for*, and *Details*. The Details column links to `guarantees.md` sections.
- Effect section:
  - one instance per anchor (`AnchorRuntime.kt:79`), shared with subscriptions;
  - `effect {}` runs on `Dispatchers.IO` unless given a context (pinning test 2);
  - must be thread-safe (pinning test 1);
  - no close hook, so resources are not released when the screen is cleared (lifecycle link).
- New "Cancellable work" section:
  - the same key cancels and joins the previous block before starting;
  - the caller suspends until its block ends;
  - a superseded caller **continues after `cancellable {}`** (pinning test 3), so put follow-up work inside the block;
  - keys compare with `equals` and are per anchor;
  - no built-in debounce: add `delay` at the start of the block (the pattern from the CLAUDE.md "Cancellable Operations" example). Pin it with a snippet test;
  - `runAnchorTest` does not cancel (link to testing).
- Events section: `Created` timing (W10), and a link to `guarantees.md#events`.

**Verify**: every behavioural sentence has a ledger row. The docs build shows only the baseline warning.

### Step 5: `docs/threading.md` (new)

Write the spec §3.1 `threading.md` sections, in this order:

- "Where your code runs": a table whose rows cite the sources below.
  - The action body: `ContainerViewModel.kt:36` for Compose, `ContainedScope.kt:28`.
  - `init` and subscription setup: `ContainerViewModel.kt:45`.
  - `.anchor {}` inside a subscription: inline in the handler's collector, `SubscriptionDsl.kt:46-53`.
  - `effect {}`: `Anchor.kt:197`.
  - Compose state collection: `Main.immediate`, `RememberAnchor.kt:63,70`.
  - The `HandleSignal` handler: the composition's `LaunchedEffect`.
  - iOS `NativeStateFlow`/`NativeSharedFlow.collect`: `Dispatchers.Main`, `NativeFlows.kt:41,65`.
- "Actions run concurrently" (pinning test 1). There is no queue; use `cancellable` or state guards to coordinate.
- "State updates":
  - `reduce` is atomic;
  - under contention the reducer may run more than once. Quote kotlinx `MutableStateFlow.update`: "*function may be evaluated multiple times, if value is being concurrently updated*";
  - so reducers must be pure;
  - do not read `state` and then write it back from outside the reducer;
  - equal states are not re-emitted, and collectors may skip intermediate states (kotlinx `StateFlow` "Strong equality-based conflation").
- "Concurrent `cancellable` calls": mutex, cancel-and-join, then start (`AnchorRuntime.kt:185-211`; existing test `CancellableTest."cancellable prevents race condition with concurrent calls"`).
- "Your Effect must be thread-safe."
- "Uncaught failures by platform": the same facts as Step 2h. Link to `errors.md`.

**Verify**: ledger rows for every row of the table. The docs build shows only the baseline warning.

### Step 6: `docs/lifecycle.md` (new, including Memory) and `docs/guarantees.md` (new)

`lifecycle.md` sections (spec §3.1):

- **Creation sequence.** Note that `init` must finish before subscriptions attach (pinning test 4), and that a domain error handled in `init` leaves subscriptions detached. Mark that second point as a **known issue** (pinning test 8).
- **Keys and reuse.**
  - The default key is the qualified name of `S` (`RememberAnchor.kt:155`).
  - The `scope` lambda runs only when the ViewModel is first created (`RememberAnchor.kt:157-161`). Quote the lifecycle-viewmodel-compose KDoc: `viewModel()` "*Returns an existing ViewModel or creates a new one*".
  - Recipe for parameterised screens: `RememberAnchor(scope = { detailAnchor(id) }, customKey = "detail-$id")`. Put it in the snippet test.
- **Configuration change.**
- **Background.**
  - UI state collection pauses below `STARTED`: `collectAsStateWithLifecycle` defaults to `minActiveState = STARTED`.
  - Actions and subscriptions keep running.
- **Clear.** Everything is cancelled (pinning test 7), and the Effect is not closed.
- **Process death.**
  - "Not supported: state restarts from `initialState`".
  - `AnchorRuntime.kt:55`, and `ContainerViewModelFactory.kt:13-18` ignores `CreationExtras`.
  - Link to plan 018 or its tracking issue.
- **iOS.** Current behaviour: each `rememberAnchor` call creates a new store that is never cleared (`anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt:30-40`). Link to plan 005.
- **Memory.**
  - Per anchor: one `MutableStateFlow`, two `MutableSharedFlow`s (signals buffer up to 64 for slow collectors), a jobs map with a mutex, the Effect instance, and the ViewModel.
  - Finished `cancellable` jobs are removed (`AnchorRuntime.kt:187,198-205`; existing `CancellableTest` cleanup tests).
  - Signals are not kept when nobody collects.
  - Everything is released only when the ViewModel is cleared.
  - iOS leaks (plan 005).
  - Qualitative only (spec Q6).

`guarantees.md` sections (spec §3.1):

- **Summary table.**
- **State.**
- **Events.**
  - Rendezvous and back-pressure (pinning test 6).
  - `Created` goes to each handler (pinning test 5).
  - Events emitted with no handler attached are dropped (pinning test 4).
  - A `!!! warning "Known issue"` admonition: emitting from an action run by a `connect` handler blocks every later `emit` for the anchor's lifetime. Workaround: `events.buffer().anchor { ... }` (spec probe P1b; 023).
- **Signals.** A `!!! note "Current behaviour, under review"` admonition that reproduces rows T1–T7 of the #266 spec §1.3 in user language, with links to #266 and plan 002. Mention the `HandleSignal` conflation and the fact that signals posted while no screen is collecting are lost. Recommend state for outcomes that must not be lost (link to best practices).
- **Effects.** They are not a stream.

**Verify**: every row of the #140 coverage checklist for topics 3, 5, 6, 7, 8, 9 and 10 now points at a section. Ledger rows exist. The docs build shows only the baseline warning.

### Step 7: `docs/best-practices.md` (new)

Do/don't items, each with a one-line reason and a link to the page that proves it:

- **Reducers:**
  - pure reducers (threading);
  - no broad `catch` (concepts, errors).
- **Data that must not be lost:**
  - model must-not-lose outcomes as state. Cite the Android "UI events" guidance (<https://developer.android.com/topic/architecture/ui-layer/events>) and give the date you checked it;
  - put follow-up work inside `cancellable` (concepts).
- **Screen setup:**
  - key parameterised screens (lifecycle);
  - a thread-safe Effect (threading);
  - do not `emit` from subscription actions until 023 lands (guarantees).
- **Performance notes:**
  - `state` versus `collectState`: write it according to the 025 outcome (Step 0 table). If -b has not landed, say that the calling scope recomposes on any change and that granular recomposition is being fixed in -b;
  - each `state`/`collectState` read starts its own collector (`RememberAnchor.kt:63,70`);
  - `anchor()` creates a new callback on every recomposition (`AnchorAction.kt:43-47`; plan 009);
  - subscriptions run in the background.

**Verify**: each item links to a section written in Steps 4–6 or 8. The docs build shows only the baseline warning.

### Step 8: `testing.md` additions, the snippet test, anchor-test KDoc

- **New section "How matching works"**:
  - matching is ordered;
  - there must be one assertion per recorded `reduce`/`post`/`emit`/`raise`/`orDie` (DocumentedLimitationsTest 4);
  - the reducer is re-run during verification, so it must be deterministic (test 5).
- **New section "What `runAnchorTest` does and does not verify"**:
  - `init` and `subscriptions` are not run (tests 1–2);
  - `cancellable` runs inline without cancelling (test 6);
  - the `effect` context is ignored;
  - `post`/`emit` are recorded, not delivered;
  - `delay` uses virtual time (test 7).
  - If plan 006 already added this section, extend it instead.
  - Do **not** tell consumers to use `AnchorRuntime`; it is `internal` (`AnchorRuntime.kt:40-41`).
- **New section "Recipes"**:
  - a fake Effect for async work;
  - testing a subscription chain by testing (1) the `Flow` function and (2) the actions it calls, each with `runAnchorTest`;
  - what `assertSignal` proves versus UI delivery (Compose-level tests arrive with plan 001).
- **Snippet test.** Put every new Kotlin block from Steps 2c, 6 (the recipe) and 8 that is not already copied from repo code into `features/main/src/commonTest/kotlin/dev/kioba/anchor/features/main/DocsSnippetsTest.kt`, or into the pinning tests. The subscription-chain recipe should reuse `MainSubscriptions.kt`'s `refresh` function.
- **KDoc.** Add KDoc to every public declaration in `anchor-test/src/commonMain` that lacks it:
  - `AnchorTestScope`, `AnchorSequenceTestScope`, `AnchorStepScope`, `GivenScope`, `StepGivenScope`, `VerifyScope`;
  - `runAnchorTest`/`runAnchorSequenceTest` if 006 has not landed.
  - The `assertEffect` KDoc must state that effects are not recorded and must point to state-based verification.
  - Also add short lifecycle KDoc on `ContainerViewModel`, `ContainerViewModelFactory` and `anchorContainerViewModelFactory`.

**Verify**:
- The sample-snippets command exits 0.
- `./gradlew :anchor-test:desktopTest` exits 0.
- Re-running the KDoc-gap scan finds no public declaration without KDoc in `anchor-test/src/commonMain`. The scan: for each `public` line, check that the preceding non-annotation line ends with `*/`.

### Step 9: `compose.md`, `errors.md`, `api.md` completeness

- `compose.md`:
  - lifecycle bullets link to `lifecycle.md`;
  - add `customKey` guidance;
  - add an `AnchorConsumer` section, copying the corrected sample from `AnchorConsumer.kt`;
  - handle W8 per the Step 0 table.
- `api.md`:
  - add `withState`, `connect`/`SubscriptionsScope`/`Flow.anchor`, `AnchorConsumer`, `EmptyEffect`, `UnitSignal`, `Created`, `AnchorSink`;
  - add an "iOS bridge" subsection: `rememberAnchor`, `nativeViewState`, `nativeSignals`, `NativeStateFlow`, `NativeSharedFlow`, `NativeCancellable`;
  - add one line: "The generated reference (plan 013) is authoritative once published."
  - Do not document `dev.kioba.anchor.internal.*` (plan 007).

**Verify**:
- Every public top-level symbol listed in spec §1.3 "Also stale" appears in `api.md` (`grep -c`).
- The docs build shows only the baseline warning.

### Step 10: Nav, llms index, README links; final Phase A gates

- Put the spec §3.1 nav in `mkdocs.yml`, without `migration.md`, which is Phase B.
- Add the new pages to `DOCS` in nav order.
- Add one line per new page to `docs/llms.txt` under "## Docs".
- README "Learn More": add a Guides link.
- Regenerate `llms-full.txt`.

**Verify**: run every row of the Commands table except the Phase B one.
- The docs build shows only the baseline warning.
- The llms regen is idempotent.
- There are no relative links.
- Both pinning suites pass, on desktop and on Native.
- The snippet tests pass.
- `./gradlew build` exits 0.
- The version guard prints nothing.

**PR2 ends here.**

### STOP — maintainer GO required before Phase B

Report to the maintainer:
- the ledger size;
- any claims you dropped for lack of evidence;
- the status of the #266 plan, plan 002, 025 and 023;
- the answers needed for spec Q1–Q7.

**Do not start Phase B without an explicit GO**, which should include the chosen migration targets.

### Phase B — decisions required

### Step 11 (B1): Signals contract

Precondition: the #266 plan and plan 002 have landed.

- Replace the interim admonition in `guarantees.md#signals` with the #266 spec §3.2 contract, verbatim.
- Update `concepts.md` Signals and the `compose.md` `HandleSignal` text to match what the #266 plan wrote.
- Add pinning tests only for delivery facts that the #266 plan's tests do not already cover.

**Verify**: the contract text is identical to the `post` KDoc that #266 added (`diff` the two strings). The docs build shows only the baseline warning.

### Step 12 (B2): `docs/migration.md`

For each library the maintainer chose:
- a concept-mapping table;
- one before/after example (the Anchor side compiled in the snippet test);
- a "no equivalent" list.

Every claim about the other library cites its official docs URL and version (V4). Add the page to the nav, `DOCS` and `llms.txt`.

**Verify**: a ledger row for every foreign claim, and the Step 10 gates.

### Step 13 (B3): Re-sync

For each of 025, 023 and plan 004 that landed after PR2:
- update the affected sections;
- make sure its pinning test was updated in that plan's PR (it should have failed there).

**Verify**: the Step 10 gates.

## Test plan

- **Test-first.** Step 1 writes 8 core and 7 test-DSL pinning tests before any doc text. They assert **current** behaviour and must pass on unmodified main code. Each wrong statement W3, W4, W11 and W12 is contradicted by a passing pinning test or an existing test:
  - W3: `ExecuteBoundaryTest."CancellationException in execute is never swallowed"`;
  - W4: DocumentedLimitationsTest 3;
  - W11: DocumentedLimitationsTest 1;
  - W12: the kotlinx docs plus spec probe U1/U2. Optionally add U1/U2 as `desktopTest` pinning tests using `Thread.setDefaultUncaughtExceptionHandler`, as in the spec's Appendix A.
- **Pattern**: `anchor/src/commonTest/kotlin/dev/kioba/anchor/CancellableTest.kt` (core) and `anchor-test/src/commonTest/kotlin/dev/kioba/anchor/test/EffectTest.kt` (DSL).
- **Regression**: the full `./gradlew build`, the `iosSimulatorArm64Test` runs for `anchor` and `anchor-test`, the feature-module tests (they are the snippet hosts), the llms idempotency check and the docs build.

## Done criteria

- [ ] Step 1 pinning tests exist and pass on desktop and iosSimulatorArm64
- [ ] W1–W7 and W9–W12 are fixed. W8 is handled per 025. The W13 pointer is added
- [ ] `docs/documents.md` is deleted
- [ ] `threading.md`, `lifecycle.md`, `guarantees.md` and `best-practices.md` are in the nav under "Guides"
- [ ] Every row of spec §1.2 maps to a page section (checklist in PR2)
- [ ] `generate-llms-full.sh` follows the nav, and `llms-full.txt` is regenerated, idempotent and free of relative links
- [ ] `mkdocs build` exits 0 with only the baseline warning
- [ ] `./gradlew build` exits 0, and the version guard prints nothing
- [ ] The claims ledger is in each PR description
- [ ] Phase B (Steps 11–13) is done after GO, or is marked BLOCKED with its reason
- [ ] The `plans/README.md` status row is updated, and plan 012's row is marked superseded by this plan

## STOP conditions

Stop and report back if:

1. Any Step 1 pinning test fails on unmodified main code. The planned sentence is wrong; report which test failed and what it observed.
2. The baseline docs build prints anything other than the `site_url` warning, or exits non-zero, **before** your changes.
3. `scripts/generate-llms-full.sh` is non-idempotent on a clean tree, or the Step 3 generic rewrite is not byte-identical for today's pages.
4. Documenting something truthfully would require changing non-comment code under `*/src/*Main/`. Route it to the owning plan instead.
5. A Native pinning run aborts with signal 6 (Kotlin/Native). A test leaked a failing coroutine; fix the test or report it.
6. You reach the Phase B gate. Phase B needs an explicit maintainer GO.
7. A "Current state" excerpt no longer matches and the change is not in the Step 0 reconciliation table.

## Maintenance notes

- **Coupling.** The pinning tests are there so that behaviour changes fail CI. Each owning plan must update the docs sections named in the test's KDoc:
  - 023 flips test 6;
  - plan 004 flips tests 4 and 8;
  - #266 changes what signals do.
  Reviewers of those plans should look for the docs change in the same PR.
- **Versions and platforms.** The docs describe master, which is deployed on every push, but the version macro names the last published release. Follow the maintainer's answer to spec Q4 for "since 0.1.9" notes and for the iosX64 lines in `llms.txt` and in the script header.
- **Strict builds.** If the maintainer sets `site_url` (spec Q5), switch the docs gate to `mkdocs build --strict` and add it to CI.
- **Reviewer focus.** Spot-check ≥ 10 ledger rows. Check that no page promises something the pinning tests don't cover. Check that `migration.md` cites the other libraries' docs and does not paraphrase from memory.
- **Plan 013 (Dokka).** Convert the `@sample` tags that hold inline code (`AnchorAction.kt:22,61,107`, `AnchorConsumer.kt:16`) into fenced examples or real sample functions, or Dokka will report unresolved samples.
