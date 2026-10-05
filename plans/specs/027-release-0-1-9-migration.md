# Spec: #237 roadmap reconciliation and 0.1.9 consumer migration readiness

- **Issue**: https://github.com/kioba/anchor/issues/237 ("🗺️ Roadmap: 0.1.7 and beyond"), a roadmap tracker rather than a single defect
- **Verdict**: PARTIALLY CONFIRMED. Most tracker items are done or declined. The real
  remaining gap is that 0.1.9 isn't ready to ship: it contains breaking changes with no migration path.
- **Severity**: P2. The live docs are misleading, and the API and DX gaps around breaking changes aren't disclosed. There's no crash, and
  consumers who stay on 0.1.8 are unaffected.
- **Companion spec**: `plans/specs/021-release-pipeline.md` (P1). The release
  pipeline itself can't publish 0.1.9 as wired. That spec owns the mechanism; this one owns the
  content.
- **Plan**: `plans/027-release-0-1-9-migration.md`
- **Written**: 2026-09-29 against origin/master `0bc430c`

---

## Part 1: Roadmap reconciliation (every item in #237)

The tracker body was last edited 2026-06-02. Its header reads "Current published version: `0.1.6`. Next target: `0.1.7`."
Reality: 0.1.7 was released 2026-06-08 and 0.1.8 on 2026-06-22 (GitHub releases, and Maven Central
`maven-metadata.xml` for all four artifacts lists 0.1.8 as the latest). Master carries
`POM_VERSION=0.1.9` (`gradle.properties`, commit `69da21d`, PR #249).

| # | Tracker item (tracker checkbox) | Reality on 2026-09-29 | Class | Evidence |
|---|---|---|---|---|
| 1 | **#182** `listen()` merged flows, no SupervisorJob `[ ]` | Fixed on master, **unreleased** (no tag contains `edc1ee3`) | DONE | PR #239 merged 2026-06-25 (`edc1ee3`), plus the Kotlin/Native containment fix `687d4e5`. `AnchorRuntime.kt:111-127` launches each flow under `SupervisorJob` + a no-op `CoroutineExceptionHandler`. Issue closed COMPLETED 06-25. Residual work (restart after a handled error, observability of contained failures) belongs to plan 003. |
| 2 | **#53** `GivenScope.effect` not suspend `[ ]` | Shipped in 0.1.7 | DONE | PR #238 merged 06-03 (`51b977b`; `git tag --contains` → 0.1.7, 0.1.8). `GivenScope.kt:14` is `public suspend fun effect(`. |
| 3 | **#167** AnchorEffect `[ ]` | Not shipped, by decision | DECLINED | Issue closed 07-06, and PR #244 closed unmerged with the rationale "already fully achievable … using the existing `anchor()` … combined with Compose's built-in `LaunchedEffect`" (the pattern is used at `features/config/.../ConfigPage.kt:42`). |
| 4 | **#168** AnchorConsumer `[ ]` | Merged, **unreleased**, with no docs and no tests | DONE, with an open pre-publication question | PR #243 merged 06-25 (`250a13e`). `git grep AnchorConsumer origin/master` matches only `anchor-compose/.../AnchorConsumer.kt`, so nothing in `docs/` covers it and no test exercises it. See decision D2. |
| 5 | **#169** NavigationSignal `[x]` | **Not on master.** The checkmark is wrong. | STALE/AMBIGUOUS (de facto declined) | Issue closed COMPLETED 2026-06-02 23:10. PR #242 was closed **unmerged** 06-03 17:50 with no comment. `git grep NavigationSignal origin/master` returns nothing. |
| 6 | **#166** error-handling sample incl. `assertNoDomainError` `[ ]` | Sample shipped; the helper isn't needed | DONE (helper: NOT NEEDED) | Closure audit comment on 06-03. The "no domain error" assertion is **implicit and enforced**: `AnchorTestScope.kt:132-136` calls `assertNull(anchor.capturedDomainError, "Expected no domain error, but got: …")` whenever `assertDomainError` is omitted, and `docs/errors.md:216-222` documents this ("The absence of `assertDomainError` is intentional and meaningful"). Verified by a scratch desktop test: raising with `onDomainError` configured and asserting only `assertRaise` fails with `Expected no domain error, but got: Boom`. No regression test in the repo pins this behavior (optional follow-up, see roadmap text). |
| 7 | Audit **#52** top error handler | Shipped | DONE | Closed 06-03. `RememberAnchorScope.kt:40-41` has the `onDomainError` and `defect` params. |
| 8 | Audit **#55** "Capture binding on ErrorAnchor" | Empty body, ambiguous | DECLINED (superseded) | Closed 06-03 as superseded by #186/#195/#198, with "Reopen with a concrete missing capability". |
| 9 | Audit **#54** "enable emitting Events" | Assertion side shipped; test-side event *seeding* doesn't exist and has no issue | DONE (assertion); seeding STALE/untracked | Closed 06-03. `VerifyScope.kt:19` `assertEvent`, and 4 cases in `EventTest.kt`. The closure asked for a new issue if seeding is wanted, and none exists. |
| 10 | **#140** documentation overhaul | Open | STILL OPEN AND REAL | Owned by the #140 verifier. This spec takes only the 0.1.9-specific doc deltas (Part 2). |
| 11 | **#133** marker interfaces decision `[x]` | Closed COMPLETED 06-02 with **no comment and no design note**; all four markers unchanged | STALE/AMBIGUOUS | Plan 020 writes the missing note. The tracker checkbox claims a decision that was never recorded. |
| 12 | Backlog **#137** CounterAnchorType dead code | Removed | DONE | Closed 06-03. `git grep -w CounterAnchorType origin/master` → 0 hits. |
| 13 | Backlog **#142** reorganize files | Open | STILL OPEN | Owned by the #142 verifier. |
| 14 | Backlog **#145** staff-review tracker | Open | STILL OPEN | Owned by the #145 verifier. |
| 15 | Header "published 0.1.6 → next 0.1.7" | 0.1.8 published; 0.1.9 on master | STALE | GitHub release v0.1.8 on 2026-06-22. Central metadata lists 0.1.8. |

**Not on the tracker but real (after 2026-06-02):** #266 (init-posted signals dropped, owned by
the #266 verifier). The 0.1.9 release train is now half-shipped: `plans/README.md`'s "Direction backlog" item 1 is
partly done, since #239 and #243 are merged, #244 is declined, and #245 is merged as 0.1.8. This spec and its
companion supersede backlog items 1 and 2.

---

## Part 2: 0.1.9 consumer migration readiness

### Problem statement (evidence)

`git rev-list --count 0.1.8..origin/master` → 18 commits. Four of them change what consumers compile against or resolve,
and none has a migration path:

1. **`listen` → `connect` rename (source-breaking).** Published 0.1.8 declares
   `public suspend inline fun <reified A> listen(` (`SubscriptionDsl.kt:68` inside
   `anchor-0.1.8-sources.jar` from Maven Central). Master has only `connect`
   (`SubscriptionDsl.kt:68`). `git grep -n -E 'listen|Deprecated' origin/master -- anchor/src` shows no
   deprecated alias. Every consumer using `listen` gets "Unresolved reference" on upgrade.
2. **`anchor-internal` artifact folded into `anchor` (`09a5864`).** The classes keep package
   `dev.kioba.anchor`. The published `anchor-test-0.1.8.pom` declares a **direct** runtime dependency on
   `anchor-internal:0.1.8`. Take a consumer who bumps `anchor` to 0.1.9 and leaves `anchor-test` at 0.1.8. Gradle
   resolves `anchor` → 0.1.9, but `anchor-internal:0.1.8` is still requested by anchor-test 0.1.8. That puts
   `RaisedException`/`DomainDefectException` in two artifacts under the same FQN.
3. **iosX64 dropped (`09a5864`).** `anchor-0.1.8.module` on Central publishes
   `iosX64ApiElements-published`, and `anchor-iosx64/0.1.8/…module` returns HTTP 200. On master the convention
   plugin declares only `iosArm64()`/`iosSimulatorArm64()` (`dev.kioba.kmp-library.gradle.kts:34-35`). A
   consumer that still declares `iosX64()` will fail variant resolution on 0.1.9.
4. **Subscription failure semantics changed (`edc1ee3` + `687d4e5`).** In 0.1.8, `subscribe()` was
   `emitter.handlers().launchIn(this)` over a `.merge()`d flow. One throwing subscription cancelled all of them, and
   with no `defect` handler the exception reached the platform last-resort handler. The 0.1.8 `ContainerViewModel`
   launches `subscribe()` inside `viewModelScope.launch`, and that file is unchanged since 0.1.8. On master, each flow runs
   under a `SupervisorJob` with `CoroutineExceptionHandler { _, _ -> }` (`AnchorRuntime.kt:114-124`). An
   exception that escapes a chain with no `defect` handler configured is therefore **silently discarded**. kotlinx.coroutines 1.11.0 KDoc, "Platform-specific last-resort
   handling": *"When no CoroutineExceptionHandler is present … On JVM, all instances of
   CoroutineExceptionHandler found via ServiceLoader and the current thread's
   Thread.uncaughtExceptionHandler are invoked. On Native, the whole application crashes."* `docs/errors.md:41`
   still says "Omitting `defect` lets unexpected exceptions propagate normally", which is no longer true inside
   `subscriptions {}`.
5. **AnchorConsumer (`250a13e`) becomes public API on first publication** with no docs or tests. Its own
   KDoc sample needs an unchecked cast, `scope.execute { (this as CounterAnchor).increment() }`
   (`AnchorConsumer.kt:26`). The decision that closed #167 rejected a second public API for something
   `anchor()` already covers "without a cast at the call site". The same rationale plausibly applies, because
   `anchor(CounterAnchor::increment)` returns a plain `() -> Unit` usable inside an `AndroidView` callback.
6. **Docs already describe unreleased 0.1.9 against the 0.1.8 coordinate.** `publish_docs.yml` deploys on every
   push to master. The live site (`curl https://kioba.github.io/anchor/llms-full.txt`) shows
   `implementation("dev.kioba.anchor:anchor:0.1.8")` (line 34) and `connect<Created> { events ->` (line 194).
   A consumer copying the docs today gets a compile error. This has been true since 2026-06-25.
7. **Stale iosX64 claims in tracked files**: `AGENTS.md:8`, `CLAUDE.md:7`, `CLAUDE.md:49`
   (`./gradlew iosX64Test`, a task that no longer exists), `docs/llms.txt:5`, and
   `scripts/generate-llms-full.sh:47` (hard-coded, so it regenerates into `docs/llms-full.txt:9`).
8. **No changelog or migration notes anywhere.** `git ls-tree -r --name-only origin/master | grep -i -E
   'change|migrat|release|news|history'` → only `.github/workflows/cut-release.yml`. Plan 016's maintenance
   note ("the next release notes must mention the artifact removal prominently") is unmet. Auto-generated
   notes can't meet it: see the companion spec, since the fold commit has no PR.

### Goals

- A consumer upgrading from 0.1.8 to 0.1.9 finds one authoritative migration section that covers every
  breaking change and behavior change, with exact before/after snippets.
- The rename costs consumers at most a deprecation warning, not a compile error (if D1 = shim).
- AnchorConsumer's fate is decided *before* it's published, and if kept, it's documented and tested.
- No tracked doc claims an iosX64 target or the old error-propagation semantics.
- The tracker (#237) text matches reality (proposed rewrite in Part 3; the maintainer posts it).

### Non-goals

- The release pipeline mechanics (trigger, notes wiring, version-sync automation). Those belong to the companion spec A.
- Redesigning subscription failure handling (restart, logging hooks). Plan 003 owns that. This spec only
  discloses the current behavior.
- The #140 documentation overhaul, plan 012 (Context Receivers / `suspend` fixes), and plan 013 (Dokka).
- Fixing #266 or any other runtime defect.
- Actually cutting the release, which is the maintainer's action.

### Proposed design

**D1: Deprecated `listen` alias (recommended: add).** In `SubscriptionsScope`:

```kotlin
/**
 * Renamed to [connect] in 0.1.9; will be removed in a future release.
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

I checked this during verification in a disposable worktree. With this shim plus a `@Suppress("DEPRECATION")` test calling
`scope.listen<…> { it }` and `scope.connect<…> { it }`, `./gradlew :anchor:desktopTest --tests
'dev.kioba.anchor.ScratchListenShimTest'` passed (1 test, 0 failures) under `explicitApi()` +
`allWarningsAsErrors`. It's binary-irrelevant because `listen` is `inline` and callers inline it, so it only affects
source compatibility.

**D2: AnchorConsumer before first publication.** The maintainer chooses one:
- (a) Keep as-is: add a `docs/compose.md` subsection, a `docs/api.md` entry, and one compose test. Note the cast in
  the docs.
- (b) Remove before 0.1.9: delete `AnchorConsumer.kt` and leave a note on #168 pointing to `anchor()`. This is consistent
  with the #167 decision and costs nothing today. After 0.1.9 it becomes a breaking removal.
- Advisor recommendation: **(b)**, because the #167 rationale applies verbatim and the only capability AnchorConsumer adds
  over `anchor()` is untyped access. (a) is acceptable, but then it must ship with docs and a test.

**D3: `CHANGELOG.md` at the repo root** (Keep-a-Changelog style, starting at 0.1.9), with a
"Migrating from 0.1.8" block. Companion spec A wires the `## [0.1.9]` section into the GitHub release body.
Content (the executor finalizes it per D1/D2):
- Breaking: `listen` → `connect` (+ alias if D1). iosX64 removed. `anchor-internal` no longer
  published: remove any explicit dependency on it, and **upgrade `anchor`, `anchor-compose` and `anchor-test`
  together** (point 2 above).
- Behavior: sibling isolation (#182). An exception that *escapes* a `connect {}` chain ends only that
  subscription, and it is not restarted. Errors inside `.anchor { }` actions are still routed per event by
  `safeExecute` (`SubscriptionDsl.kt:49-52`). Without a `defect` handler, the escaping exception is now
  discarded on every platform, so configure `defect` to observe it.
- Added: AnchorConsumer (if D2 = a).
- Dependencies: `org.jetbrains.compose.runtime:runtime` (api of anchor-compose) 1.10.3 → 1.11.1 (re-derive at
  release time, because open PR #267 bumps 18 deps).

**D4: 0.1.9 doc deltas**, minimal and factual:
- Remove iosX64 from `AGENTS.md:8`, `CLAUDE.md:7`, `CLAUDE.md:49`, `docs/llms.txt:5` and
  `scripts/generate-llms-full.sh:47`, then regenerate `docs/llms-full.txt`.
- `docs/errors.md`: add a subscription-specific sentence next to line 41.
- `docs/api.md`: add a `SubscriptionsScope.connect<A>` entry (the API page doesn't mention subscriptions at all)
  and AnchorConsumer if D2 = a.
- README "Learn More": link to CHANGELOG.

### Alternatives considered

- **Ship the rename without an alias and rely on release notes.** This is valid pre-1.0 and costs one find/replace for
  consumers (PR #239 body). It was rejected as the default because the alias costs 12 lines, is verified to compile, and turns a hard
  break into a warning. It stays available if the maintainer prefers a clean break (D1 = no).
- **A relocation-only `anchor-internal:0.1.9` artifact, or a Gradle capability declaration so mixed
  versions fail loudly.** Rejected. Relocation re-adds per-release published files that `09a5864` removed to
  stay under the Maven Central quota. A capability conflict turns a rare, self-inflicted mixed-version edge case into a
  hard resolution error for everyone who hits it. A migration note ("upgrade all three together") is proportionate.
- **A docs page (`docs/migration.md`) instead of a root CHANGELOG.** Rejected as the primary vehicle: the docs site
  is master-tracking and version-less, while a CHANGELOG is the conventional per-version record and is what spec A
  extracts. The CHANGELOG can be linked from the docs.
- **Hold 0.1.9 until #266 / plan 003 / plan 007 land.** Not recommended. The live docs have been wrong against
  0.1.8 for three months (point 6), and bundling more breaking changes (plan 007's opt-in gate) would enlarge the
  migration. This is left as open question Q3.

### Behavior and API changes

| Change | Breaking? |
|---|---|
| `@Deprecated listen` alias (D1) | Non-breaking (restores 0.1.8 source compatibility with a warning) |
| AnchorConsumer removal (D2 = b) | Non-breaking relative to every *published* version (never released); breaking relative to master |
| CHANGELOG, doc deltas | Non-breaking (docs only) |

### Acceptance criteria

- `CHANGELOG.md` exists with a `## [0.1.9]` section that names every item in "Problem statement" points 1-5,
  each with a before/after or an action for the consumer.
- If D1 = shim: a test in `anchor/src/commonTest` subscribes via deprecated `listen`, emits an event, and
  observes it. `./gradlew :anchor:desktopTest` passes.
- If D2 = a: `docs/compose.md` + `docs/api.md` document AnchorConsumer and a compose test exercises it. If D2 = b:
  `git grep AnchorConsumer` → no hits outside CHANGELOG/history.
- `git grep -n iosX64 -- ':!CHANGELOG.md'` → no hits. `bash scripts/generate-llms-full.sh` is idempotent.
- `docs/errors.md` states the subscription containment behavior.
- `./gradlew build` exits 0, and the `pr check` doc-version gates pass (version strings untouched).

### Open questions for the maintainer

- **Q1 (D1)**: Add the deprecated `listen` alias? If yes, which release removes it (0.2.0?).
- **Q2 (D2)**: Keep AnchorConsumer (document + test) or remove it before its first publication?
- **Q3**: Ship 0.1.9 with what's on master now, or wait for #266 / plan 003 / plan 007 (the opt-in gate is itself
  a breaking change and would join this migration section)?
- **Q4**: Is silently discarding unhandled subscription exceptions (`AnchorRuntime.kt:121`) the intended 0.1.9
  contract? The code comment at `AnchorRuntime.kt:117` says "The JVM/Android default just logs it", but plan 003
  (§Why, point 2) describes the 0.1.8 path as a "process crash on Android". The release note in D3 uses
  platform-neutral wording backed by the kotlinx KDoc. The design question belongs to plan 003.
- **Q5 (tracker hygiene, no code)**: Re-label #169 and #133 closures (COMPLETED → not planned / pending plan
  020)? Post the Part 3 text to #237 and retitle it?

---

## Part 3: Proposed replacement text for issue #237

> Title: `🗺️ Roadmap: 0.1.9 and beyond`

```markdown
A snapshot of work planned for Anchor, grouped by phase. Each item links to its tracking issue.

Current published version: `0.1.8`. Next target: `0.1.9` (code complete on master, not yet released).

## 0.1.9 — release train

Already on master (unreleased):

- [x] **#182** — `connect()` subscriptions isolated per-flow with `SupervisorJob` (#239) + Kotlin/Native containment (687d4e5).
- [x] **Rename** `SubscriptionsScope.listen` → `connect` (#239). ⚠️ Source-breaking.
- [x] **#168** — `AnchorConsumer` composable (#243). ⚠️ Decide keep/remove before first publication.
- [x] `anchor-internal` folded into `anchor` (09a5864). ⚠️ Artifact no longer published.
- [x] `iosX64` target dropped (09a5864). ⚠️ Consumers must drop `iosX64()`.
- [x] Maven Central publishing gated on a tag instead of a weekly cron (0c0bb9d).

Before cutting 0.1.9:

- [ ] CI unblock: `android-actions/setup-android` v4 (#268) — publish job fails on v3.
- [ ] Release pipeline: `cut release` pushes the tag with `GITHUB_TOKEN`, which cannot trigger `publish package`; release notes omit commits without PRs; `AGENTS.md` published-version never auto-updates.
- [ ] Migration notes + `CHANGELOG.md` for the breaking changes above; optional deprecated `listen` alias; 0.1.9 doc deltas (iosX64 mentions, subscription error semantics, `connect`/`AnchorConsumer` in the API reference).

## Next — correctness

- [ ] **#266** — Signals posted from `init` are dropped before a collector attaches.
- [ ] Subscriptions: restart after a handled error; make contained (discarded) failures observable. *(needs an issue)*

## Pre-1.0

- [ ] **#140** — Documentation overhaul.
- [ ] **#133** — Marker interfaces: the issue was closed without a recorded decision; write and link the design note.
- [ ] **#145** — Staff-engineer review tracker. Close once remaining children are scheduled or resolved.

## Backlog

- **#142** — Reorganize files by concept. Internal-only.
- Test DSL: seeding events from a test (the open half of #54). *(needs an issue if wanted)*
- Test DSL: add a regression test pinning the implicit "no domain error" check (`AnchorTestScope.kt:135`).

## Closed since the previous roadmap

- **#53** `GivenScope.effect` is `suspend` (#238, shipped in 0.1.7).
- **#166** Error-handling reference sample (`features/config`). `assertNoDomainError` is not needed: omitting `assertDomainError` already fails the test if `onDomainError` ran.
- **#52** top-level handlers (`onDomainError`/`defect` in `create()`), **#54** `assertEvent`, **#55** superseded, **#137** dead code removed.

## Declined

- **#167** `AnchorEffect` — use `anchor()` + `LaunchedEffect` (#244 closed).
- **#169** `NavigationSignal` — #242 closed unmerged; not shipped.
```

## Relationship to existing plans

- **Supersedes** `plans/README.md` Direction backlog items 1 (ship the parked release train) and 2 (tracker
  reconciliation).
- **Delivers** plan 016's maintenance note (release notes must announce the anchor-internal removal).
- **Complements** plan 003 (discloses the current containment behavior; the design stays in 003), plan 012 and #140
  (touches different doc lines, and only the 0.1.9 deltas), and plan 020 (#133 note).
- **Depends on** companion spec/plan A for the release to publish at all, and on PR #268 for CI.
- Note for the coordinator: plan 013 still lists `anchor-internal` among the published modules (stale since
  `09a5864`).
