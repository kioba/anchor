# Plan 027: Make 0.1.9 upgradable: `listen` alias, AnchorConsumer decision, CHANGELOG with migration notes, 0.1.9 doc deltas

> **Executor instructions**: This is an INVESTIGATE-THEN-ACT plan with a hard
> decision gate. Complete Phase A (investigation), write the findings into
> this file under "Investigation results", and STOP for maintainer review
> before executing Phase B. Do not perform Phase B in the same run unless the
> operator explicitly pre-authorized it *and* supplied the D1/D2 decisions.
> Follow every step, run every verification command, and confirm the expected
> result before moving on. If anything in the "STOP conditions" section
> occurs, stop and report. Do not improvise. When done (either phase), update
> the status row for this plan in `plans/README.md`, unless a reviewer
> dispatched you and told you they maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionDslTest.kt anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorConsumer.kt docs/ README.md AGENTS.md CLAUDE.md scripts/generate-llms-full.sh CHANGELOG.md`
> Expected drift: companion plan 021 changes `AGENTS.md:7` only.
> For any other change to an in-scope file, compare the "Current state"
> excerpts against the live files before proceeding. On a mismatch, treat it
> as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: M (A: S, B: M)
- **Risk**: LOW (docs, a deprecated alias, and a decision on unreleased API; no runtime behavior change)
- **Depends on**: none to execute. The *release* of 0.1.9 additionally needs companion plan
  `plans/021-release-pipeline.md` and PR #268. If D2 = keep, the AnchorConsumer test needs
  `plans/001-anchor-compose-test-harness.md` (anchor-compose has no test source set on master).
- **Category**: release / docs / api
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: `plans/specs/027-release-0-1-9-migration.md` (also holds the full #237
  reconciliation table and the proposed tracker text)

## Why this matters

Master is 18 commits past the last release (0.1.8). It contains four consumer-visible breaking or behavior
changes (the `listen` → `connect` rename, `anchor-internal` no longer published, the `iosX64` target
dropped, and silently contained subscription failures) plus one new public API (`AnchorConsumer`).
None of them has a changelog entry, migration note or deprecation path. Meanwhile the docs site, deployed from master
on every push, already tells readers to use `connect<Created>` with `dev.kioba.anchor:anchor:0.1.8`,
a combination that doesn't compile, because published 0.1.8 only has `listen`. Shipping 0.1.9 fixes that mismatch,
and this plan makes 0.1.9 safe to ship.

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt:55-81` (master has only `connect`;
  published 0.1.8 has the identical body named `listen` at line 68 of `anchor-0.1.8-sources.jar`):

  ```kotlin
    /**
     * Connects a handler to internal [Event]s of type [A].
     ...
     */
    public suspend inline fun <reified A> connect(
      crossinline block: (Flow<A>) -> Flow<*>,
    ) where A : Event {
      wrap {
        block(filterIsInstance())
      }
    }

    @PublishedApi
    internal suspend fun wrap(
      func: suspend Flow<Event>.() -> Flow<*>,
    ) {
      flows.add(chain.func())
    }
  }
  ```

- `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionDslTest.kt:73-124`: the pattern to copy, which is
  `` `sibling subscription keeps receiving events when another subscription throws` ``. It builds an
  `AnchorRuntime(subscriptions = { connect<…> { … } })`, launches `subscribe()`, waits on
  `anchor._emitter.subscriptionCount`, emits, and awaits collection.
- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorConsumer.kt:39-44` (unreleased,
  merged in `250a13e` / PR #243; the only file on master mentioning `AnchorConsumer`):

  ```kotlin
  @Composable
  public fun AnchorConsumer(
    content: @Composable (AnchorScope<*, *>) -> Unit,
  ) {
    content(LocalAnchor.current)
  }
  ```

  Its KDoc sample (line 26) requires `scope.execute { (this as CounterAnchor).increment() }`.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:111-127`: each subscription
  flow is launched under `SupervisorJob(parent = …)` + `CoroutineExceptionHandler { _, _ -> }`.
  Upstream of that, `handlers()` (lines 103-109) wraps each flow in
  `catch { e -> safeExecute(…, onDomainError, defect) { throw e } }`, so the flow ends after any error whether or
  not a handler is configured.
- `docs/errors.md:41`: "Both handlers are optional. Omitting `onDomainError` means an unhandled `raise()`
  crashes the coroutine. Omitting `defect` lets unexpected exceptions propagate normally."
- `docs/concepts.md:86-96`: the "Events & Subscriptions" section with a `connect<Created>` example and
  nothing about isolation or errors.
- `docs/compose.md:85-102`: the "Dispatching Actions" section, which has no non-composable-callback guidance.
- `docs/api.md`: no `SubscriptionsScope`/`connect` entry. The Compose API section is lines 37-94.
- Stale iosX64 mentions: `AGENTS.md:8`, `CLAUDE.md:7`, `CLAUDE.md:49` (`./gradlew iosX64Test                # iOS x64 tests`),
  `docs/llms.txt:5`, and `scripts/generate-llms-full.sh:47` (a hard-coded `- Platforms: Android, iOS (iosX64, iosArm64, iosSimulatorArm64), Desktop (JVM)`,
  regenerated into `docs/llms-full.txt:9`). The build targets only `iosArm64()`/`iosSimulatorArm64()`
  (`convention-plugins/src/main/kotlin/dev.kioba.kmp-library.gradle.kts:34-35`).
- No `CHANGELOG.md` exists. `README.md:86-92` "Learn More" lists docs links only.
- CI gates on docs (`pr_check.yml`): `version_check` requires README/mkdocs/CLAUDE.md (and AGENTS.md once
  plan 021 lands) to agree on the **published** version, so don't change version strings.
  `llms_check` requires `docs/llms-full.txt` to equal the script output.
- Verified during planning, in a disposable worktree at `0bc430c`: the D1 alias below plus a
  `@Suppress("DEPRECATION")` test compiled and passed via `./gradlew :anchor:desktopTest` under
  `explicitApi()` + `allWarningsAsErrors`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Core tests (fast loop) | `./gradlew :anchor:desktopTest` | exit 0 |
| Single test | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.SubscriptionDslTest'` | exit 0 |
| Native check for the new test | `./gradlew :anchor:iosSimulatorArm64Test --tests 'dev.kioba.anchor.SubscriptionDslTest'` | exit 0 (macOS only) |
| iosX64 sweep | `git grep -n iosX64 -- ':!CHANGELOG.md'` | no output |
| llms regen | `bash scripts/generate-llms-full.sh` | exit 0 |
| llms idempotency | `bash scripts/generate-llms-full.sh && git diff --quiet docs/llms-full.txt` after committing | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- Phase A: this plan file (append "Investigation results").
- Phase B: `anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt` (D1 alias only),
  `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionDslTest.kt` (one new test),
  `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorConsumer.kt` (D2 only),
  `CHANGELOG.md` (create), `README.md` ("Learn More" link only), `AGENTS.md:8`, `CLAUDE.md:7,49`,
  `docs/llms.txt:5`, `scripts/generate-llms-full.sh:47`, `docs/llms-full.txt` (regenerated only),
  `docs/errors.md`, `docs/concepts.md`, `docs/compose.md`, `docs/api.md` (the additions listed in B5).

**Out of scope**:
- Any change to `AnchorRuntime.kt` or subscription runtime behavior (plan 003 owns the restart/observability design).
- Version strings anywhere (CI-gated; the release automation bumps them).
- The release pipeline (plan 021), the Context Receivers / `suspend` doc fixes (plan 012), and the #140 overhaul.
- Posting to GitHub (tracker text, issue relabels). Put it in your report for the maintainer.

## Git workflow

- Branch: `release/0.1.9-migration`
- Commit style: gitmoji, e.g. `✨ Add deprecated listen alias for connect`,
  `📝 Add CHANGELOG with 0.1.9 migration notes`, `📝 Drop iosX64 from docs`,
  `🔥 Remove unreleased AnchorConsumer` (only if D2 = remove)
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A: Investigation (always safe)

**A1**: Run the drift check. Confirm 0.1.9 is still unreleased:
`curl -s https://repo1.maven.org/maven2/dev/kioba/anchor/anchor/maven-metadata.xml | grep -c '<version>0.1.9</version>'`
→ `0`, and `git tag --list 0.1.9` → empty.

**Verify**: both are unreleased. If 0.1.9 has been published, STOP. The plan's premise (fix before
first publication) no longer holds, and D2 = remove would now be a breaking removal.

**A2**: Re-verify the facts the CHANGELOG will assert:
- `git grep -n -E 'fun <reified A> (listen|connect)' -- anchor/src` → only `connect`.
- `git grep -n iosX64` → the five files listed in Current state (plus `docs/llms-full.txt`).
- `curl -s https://repo1.maven.org/maven2/dev/kioba/anchor/anchor-test/0.1.8/anchor-test-0.1.8.pom | grep -A2 '<artifactId>anchor-internal'`
  → a runtime dependency on `anchor-internal` 0.1.8.
- `git diff 0.1.8..HEAD -- gradle/libs.versions.toml`: record consumer-visible bumps (the `compose-plugin`
  version drives `org.jetbrains.compose.runtime:runtime`, the `api` of anchor-compose; open PR #267 may change it).

**Verify**: each result matches, or record the delta.

**A3**: Append `## Investigation results` to this file with the A1-A2 results and the decisions
needed from the maintainer:
- **D1**: add the deprecated `listen` alias (recommended), yes or no. If yes, which release removes it?
- **D2**: AnchorConsumer: **keep** (document now; its test waits for plan 001's harness) or **remove**
  before first publication (advisor recommendation, for consistency with the #167/#244 closure: "I'd rather not add
  a second public API that does the same thing").
- **Q3**: ship 0.1.9 now vs. wait for #266 / plan 003 / plan 007.
- **Q4**: accept the platform-neutral CHANGELOG wording for contained subscription failures (B4).

Set this plan's row in `plans/README.md` to BLOCKED (awaiting maintainer GO + D1/D2). **STOP here and report.**

### Phase B: Execution (only after maintainer GO with D1/D2 answered)

### Step B1 (D1 = yes): failing test for the alias first

Append to `SubscriptionDslTest` (same file, same imports; they already include `CompletableDeferred`,
`Job`, `cancelAndJoin`, `coroutineScope`, `onEach`, `launch`, `runBlocking`, `withTimeout`, `yield`):

```kotlin
  @Test
  @Suppress("DEPRECATION")
  fun `deprecated listen alias still delivers events`(): Unit =
    runBlocking {
      val received = mutableListOf<Int>()
      val anchor =
        AnchorRuntime<EmptyEffect, TestState, TestError>(
          initialState = { TestState(value = 0) },
          effectScope = { EmptyEffect },
          subscriptions = {
            listen<SubTestEvent.Survive> { events ->
              events.onEach { event -> received.add(event.value) }
            }
          },
        )

      coroutineScope {
        val supervisorReady = CompletableDeferred<Job>()
        launch {
          with(anchor) {
            supervisorReady.complete(subscribe())
          }
        }
        val supervisor = supervisorReady.await()

        withTimeout(2_000) {
          while (anchor._emitter.subscriptionCount.value < 1) yield()
        }
        anchor._emitter.emit(SubTestEvent.Survive(value = 5))

        withTimeout(2_000) {
          while (received.isEmpty()) yield()
        }
        assertEquals(listOf(5), received)

        supervisor.cancelAndJoin()
      }
    }
```

**Verify**: `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.SubscriptionDslTest'` **fails to
compile** with an unresolved reference to `listen`. That's the regression proof.

### Step B2 (D1 = yes): add the alias

In `SubscriptionDsl.kt`, directly after `connect` (before `@PublishedApi internal suspend fun wrap`):

```kotlin
  /**
   * Renamed to [connect] in 0.1.9. Kept as a deprecated alias so 0.1.8 call sites keep
   * compiling; it will be removed in <REMOVAL_VERSION from D1>.
   */
  @Deprecated(
    message = "Renamed to connect",
    replaceWith = ReplaceWith("connect<A>(block)"),
    level = DeprecationLevel.WARNING,
  )
  public suspend inline fun <reified A> listen(
    crossinline block: (Flow<A>) -> Flow<*>,
  ) where A : Event {
    connect<A>(block)
  }
```

**Verify**:
- `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.SubscriptionDslTest'` → all pass, including the new test.
- `./gradlew :anchor:iosSimulatorArm64Test --tests 'dev.kioba.anchor.SubscriptionDslTest'` → pass (macOS).
- `git grep -n 'listen<' -- features/ anchor/src/commonMain` → no call sites (the samples must use `connect`).

### Step B3: apply D2

- **D2 = remove**: `git rm anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorConsumer.kt`.
  **Verify**: `git grep -n AnchorConsumer -- ':!CHANGELOG.md' ':!plans'` → no output, and
  `./gradlew :anchor-compose:build` → exit 0.
- **D2 = keep**: no code change here. It's documented in B5. Record in your report that its test belongs in
  plan 001's harness (anchor-compose has no `commonTest` on master).
  **Verify**: the file is unchanged.

### Step B4: create `CHANGELOG.md`

Create it at the repo root. The `## [0.1.9]` heading format is what `scripts/release-notes.sh` (plan
021) extracts, so keep `## [x.y.z]` exactly. Drop the bracketed alternatives that don't apply to D1/D2:

````markdown
# Changelog

All notable changes to the published artifacts `dev.kioba.anchor:anchor`, `anchor-compose` and
`anchor-test`. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Anchor is pre-1.0:
any release may contain breaking changes, and they are always listed under **Breaking**.

## [0.1.9] - unreleased

### Migrating from 0.1.8

Upgrade all three artifacts together:

```kotlin
implementation("dev.kioba.anchor:anchor:0.1.9")
implementation("dev.kioba.anchor:anchor-compose:0.1.9")
testImplementation("dev.kioba.anchor:anchor-test:0.1.9")
```

### Breaking

- **`SubscriptionsScope.listen` is renamed to `connect`** (#239). [D1=yes: `listen` still compiles
  as a deprecated alias and will be removed in <REMOVAL_VERSION>.] Replace `listen<` with `connect<` and
  `listen(::` with `connect(::`:

  ```kotlin
  // 0.1.8
  subscriptions = { listen<Created> { events -> events.anchor { loadData() } } }
  // 0.1.9
  subscriptions = { connect<Created> { events -> events.anchor { loadData() } } }
  ```

- **The `iosX64` (Intel simulator) target is no longer published.** Remove `iosX64()` from your
  Kotlin Multiplatform targets (Apple Silicon simulators use `iosSimulatorArm64`), or stay on 0.1.8.
- **`dev.kioba.anchor:anchor-internal` is no longer published.** `RaisedException` and
  `DomainDefectException` now ship inside `anchor` in the same package (`dev.kioba.anchor`), so no import
  changes are needed. Remove any explicit `anchor-internal` dependency. Don't combine `anchor-test:0.1.8` with
  `anchor:0.1.9`: `anchor-test:0.1.8` depends on `anchor-internal:0.1.8` directly, which puts two copies of
  those classes on the test classpath.

### Changed

- **Subscriptions are isolated from each other** (#239, fixes #182). An exception in one `connect {}`
  chain no longer cancels the other subscriptions of the same anchor.
- **Failed subscriptions end quietly.** Errors thrown inside an `.anchor { }` action are still routed to
  `onDomainError`/`defect` per event without stopping the subscription. An exception that *escapes* a
  `connect {}` chain ends that subscription, and it is not restarted. That covers an exception thrown elsewhere in the chain,
  or one that no configured handler absorbs. If `defect` is configured, it is invoked first. Otherwise the
  exception is now discarded on every platform. In 0.1.8 it cancelled all subscriptions of the anchor and
  reached the platform's last-resort coroutine exception handling (on Kotlin/Native that aborts the
  process). Configure `defect` in `create()` to observe these failures.
- `anchor-compose` exposes `org.jetbrains.compose.runtime:runtime` <A2 value, e.g. 1.11.1> (was 1.10.3) as an
  `api` dependency.

[D2=keep:
### Added

- `AnchorConsumer` composable (#243): exposes the current `AnchorScope` to non-composable callbacks.
  Prefer `anchor(MyAnchor::action)`, which is type-safe. `AnchorConsumer` requires casting the receiver.]
````

**Verify**: `bash scripts/release-notes.sh 0.1.9` prints the section, if plan 021 has landed. Otherwise
run the awk from that plan's B3 against the file. Output starts with `### Migrating from 0.1.8` and contains no
`[D1=`/`[D2=`/`<A2` placeholders: `grep -n -E '\[D[12]=|<A2|<REMOVAL' CHANGELOG.md` → no output.

### Step B5: 0.1.9 doc deltas

1. iosX64: `AGENTS.md:8`, `CLAUDE.md:7` and `docs/llms.txt:5` → "iOS (iosArm64, iosSimulatorArm64)". Delete
   `CLAUDE.md:49` (`./gradlew iosX64Test …`). `scripts/generate-llms-full.sh:47` →
   `- Platforms: Android, iOS (iosArm64, iosSimulatorArm64), Desktop (JVM)`.
2. `docs/errors.md:41`: append: "Inside `subscriptions { connect { … } }`, errors from `.anchor { }` actions
   are routed per event like any other action. An exception that escapes the chain ends only that
   subscription. Since 0.1.9, without a `defect` handler that exception is discarded silently, so configure
   `defect` if you need to observe subscription failures."
3. `docs/concepts.md` (after the `connect<Created>` example, line 96): one sentence saying each `connect`
   chain runs independently, a failure in one doesn't stop the others, and a failed chain isn't restarted.
   Link to errors.md.
4. `docs/api.md`: under "Core API", after `RememberAnchorScope.create(...)`, add
   `### SubscriptionsScope.connect<A>(block)` with one line ("Connects a handler to internal events of type `A`;
   `block` transforms `Flow<A>` into a flow that is collected for the anchor's lifetime.") and the
   concepts.md link. If D1 = yes, add "(`listen` is a deprecated alias.)". If D2 = keep, add
   `### AnchorConsumer(content)` to the Compose API section, before `PreviewAnchor` (line 80).
5. `docs/compose.md`, at the end of "Dispatching Actions" (after line 102): add a "Non-composable callbacks"
   paragraph showing that the `() -> Unit` returned by `anchor()` can be captured in an `AndroidView` factory
   (`setOnClickListener { increment() }`). If D2 = keep, follow it with the `AnchorConsumer` alternative and its cast caveat.
6. `README.md` "Learn More" (lines 86-92): add `- [**Changelog**](CHANGELOG.md): Release notes and migration guides.`

**Verify**: `git grep -n iosX64 -- ':!CHANGELOG.md'` → no output. `git diff --stat` touches only in-scope
files. `git diff | grep -E '^[-+].*[0-9]+\.[0-9]+\.[0-9]+'` shows no changed version strings outside
`CHANGELOG.md`.

### Step B6: regenerate the llms snapshot

`bash scripts/generate-llms-full.sh`, then commit `docs/llms-full.txt`.

**Verify**: running the script a second time leaves `git status --short docs/llms-full.txt` empty, and
`docs/llms-full.txt:9` no longer mentions iosX64.

### Step B7: full gate

**Verify**: `./gradlew build` → exit 0 (includes native tests on macOS).

### Step B8: report for the maintainer (do NOT post anywhere)

Include:
- The D1/D2 outcome.
- The final `## [0.1.9]` section.
- The proposed #237 replacement text from the spec (Part 3), updated with the D2 outcome.
- The tracker hygiene suggestions: relabel #169 (not shipped; PR #242 closed unmerged) and #133 (no decision recorded; plan 020).
- A reminder that the release needs PR #268 plus plan 021 first.

## Test plan

- Written first: B1's `deprecated listen alias still delivers events` must fail to compile before B2 and pass
  after, on desktop and iosSimulatorArm64. It pins source compatibility for 0.1.8 call sites.
- D2 = remove: a `git grep` + `:anchor-compose:build` check. D2 = keep: the test is deferred to plan 001's harness
  (recorded, not silently skipped).
- Docs: the iosX64 grep, the placeholder grep, and llms idempotency are the checks. `./gradlew build` is the global gate.

## Done criteria

Phase A:
- [ ] "Investigation results" appended with A1-A2 results and D1/D2/Q3/Q4 questions
- [ ] `plans/README.md` row set to BLOCKED (awaiting maintainer GO)

Phase B:
- [ ] D1 applied: alias + passing test (desktop and iosSimulatorArm64), or the decision "no alias" recorded in the CHANGELOG wording
- [ ] D2 applied: file removed and grep clean, or kept, with docs added and the test gap handed to plan 001
- [ ] `CHANGELOG.md` has a placeholder-free `## [0.1.9]` section covering the rename, iosX64, anchor-internal, isolation, and containment
- [ ] No `iosX64` outside CHANGELOG. `docs/errors.md`, `docs/concepts.md`, `docs/api.md`, `docs/compose.md` and README updated per B5
- [ ] `docs/llms-full.txt` regenerated and idempotent. No version strings changed
- [ ] `./gradlew build` exits 0
- [ ] B8 report delivered. `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- (Built into the plan) the end of Phase A is a mandatory stop.
- 0.1.9 (or later) is already on Maven Central (A1).
- `SubscriptionDsl.kt` or `AnchorConsumer.kt` has moved or been renamed (for example by a #142 reorganization). Re-derive paths
  and re-confirm with the maintainer.
- B1's test compiles *before* B2 (someone already added `listen`). Report instead of duplicating it.
- The alias triggers a warning-as-error in any module (a `features/` sample still calls `listen`, for example). Fix the call
  site to `connect` only if it's a sample. Otherwise stop.
- `scripts/generate-llms-full.sh` is non-idempotent on a clean tree.
- The maintainer asks to also change runtime behavior (for example logging contained failures). That is plan 003's scope, so don't
  fold it in here.

## Maintenance notes

- Keep the CHANGELOG as the single source for release notes. Plan 021's `cut release` refuses to tag
  without a `## [<POM_VERSION>]` section, so every future release needs an entry *before* cutting.
- When the alias's removal release comes, delete `listen`, delete its test, and list the removal under
  **Breaking** in that release's section.
- Plan 003 (subscription resilience) will change the "Failed subscriptions end quietly" behavior. Whoever lands
  it must update this CHANGELOG wording and `docs/errors.md`/`docs/concepts.md` in the same PR.
- The `AnchorConsumer.kt` KDoc says "@see anchor For the preferred way". If D2 = keep, plan 009 (memoize
  `anchor()` callbacks) makes that preference stronger. Mention it in its docs when 009 lands.
- Stale reference for the coordinator: `plans/013-dokka-api-reference.md` still lists `anchor-internal` as a
  published module.
