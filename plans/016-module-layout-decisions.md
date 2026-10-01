# Plan 016: Investigate-and-decide — anchor-internal module fold-in and the "anchorS" root project name

> **Triage note (2026-09-29, origin/master `0bc430c`)**: Input for the anchorS rename (from the #142 verifier): the rootProject name `anchorS` leaks into the published klib manifest (`unique_name=anchorS:anchor`).

> **Executor instructions**: This is an INVESTIGATE-THEN-ACT plan with a hard
> decision gate: complete Phase A (investigation), write the findings into
> this file under "Investigation results", and STOP for maintainer review
> before executing Phase B. Do not perform Phase B in the same run unless the
> operator explicitly pre-authorized it. When done (either phase), update the
> status row in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor-internal/ settings.gradle.kts anchor/build.gradle.kts anchor-test/build.gradle.kts`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P3
- **Effort**: M (A: S, B: M)
- **Risk**: MED (published-artifact change)
- **Depends on**: plans/007-internal-api-opt-in.md (touches the same files'
  imports; land 007 first to avoid conflicts)
- **Category**: tech-debt
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

Two module-layout oddities cost comprehension and add publish surface:

1. **`anchor-internal`** is a published module containing exactly one file —
   `AnchorExceptions.kt` (`RaisedException`, `DomainDefectException`, package
   `dev.kioba.anchor`) — `api()`-ed by both `anchor` and `anchor-test`. Since
   `anchor-test` already depends on `api(projects.anchor)` (which itself
   `api()`s anchor-internal), the direct edge is redundant, and the module's
   reason to exist is not documented anywhere. If there is no cycle-breaking
   reason, folding it into `anchor` removes a published artifact, a build
   module, and a "what is this?" for every new contributor.
2. **`rootProject.name = "anchorS"`** (`settings.gradle.kts:37`) — looks like
   a typo, but renaming a root project can affect IDE project identity,
   included-build references, and iOS framework metadata; the intent must be
   established before "fixing" it.

Both need investigation before action — acting on a wrong guess here breaks
consumers (`anchor-internal` is on Maven Central) or the build.

## Current state

- `anchor-internal/src/commonMain/kotlin/dev/kioba/anchor/AnchorExceptions.kt`
  — sole source file; `RaisedException : CancellationException` (with `token`
  for standalone `recover`), `DomainDefectException : RuntimeException`.
- `anchor/build.gradle.kts:10` → `api(projects.anchorInternal)`;
  `anchor-test/build.gradle.kts:13-14` → `api(projects.anchor)` AND
  `api(projects.anchorInternal)`.
- `anchor-internal/build.gradle.kts` — READ IT in Phase A (publication status,
  plugins; it was not fully audited at planning time).
- `settings.gradle.kts:37` → `rootProject.name = "anchorS"`; the repo, docs,
  and Maven `POM_GROUP_ID=dev.kioba.anchor` all say "anchor".
- Consumers of the exception types: `grep -rn "RaisedException\|DomainDefectException" --include="*.kt" anchor anchor-test anchor-compose features | grep -v build/` —
  many sites in `anchor` internals + `anchor-test`; external consumers can
  legitimately `catch` them.
- Maven Central history: `dev.kioba.anchor:anchor-internal` is published at
  least at 0.1.5 (README/installation docs list only anchor, anchor-compose,
  anchor-test — consumers get anchor-internal transitively via Gradle module
  metadata).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Module history | `git log --follow --oneline -- anchor-internal/ \| tail -20` | creation context |
| Name history | `git log -S "anchorS" --oneline -- settings.gradle.kts` | when/why introduced |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- Phase A: this plan file (writing results); no code changes.
- Phase B (post-approval): `anchor-internal/**` (delete),
  `anchor/src/commonMain/kotlin/dev/kioba/anchor/AnchorExceptions.kt` (create,
  moved content), `anchor/build.gradle.kts`, `anchor-test/build.gradle.kts`,
  `settings.gradle.kts`, `umbrella/build.gradle.kts` (if it exports
  anchor-internal), CLAUDE.md module list.

**Out of scope**:
- Renaming/republishing anything on Maven Central retroactively.
- The `umbrella` module's broader purpose (only its anchor-internal references).

## Git workflow

- Branch: `refactor/module-layout`
- Commit style: gitmoji, e.g. `🔥 Fold anchor-internal into anchor` /
  `♻️ Rename root project to anchor`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

## Investigation results (2026-07-07)

Triggered by a real Maven Central publish-quota incident (weekly cron
auto-releases blowing past the 1000-files/month cap), not a routine plan
sweep — investigated and executed the fold in the same session with explicit
maintainer GO.

- **A2**: `anchor-internal/build.gradle.kts` used the plain `dev.kioba.kmp-library`
  + `dev.kioba.publish` plugins, no differences from any other published
  module — same android/desktop/iosArm64/iosSimulatorArm64 target matrix, so
  it cost a full extra module's worth of published files every release.
- **A3**: No cycle. `anchor-test`'s direct `api(projects.anchorInternal)` was
  redundant — it already got the types transitively via `api(projects.anchor)`.
  No other module (`anchor-compose`, `convention-plugins`) depended on
  anchor-internal directly.
- **A4**: `umbrella/build.gradle.kts` did not reference anchor-internal in its
  `export`/`api` lists — no impact there.
- Maven Central / docs: README/AGENTS.md never documented `anchor-internal`
  as a coordinate to add directly — only reachable transitively via `anchor`.
- **Verdict: GO on the fold.** `anchorS` rename (the other half of this plan)
  was NOT evaluated or executed — out of scope for this session, left TODO.

### Phase B — Executed (fold only, B1)

Moved `AnchorExceptions.kt` into `anchor/src/commonMain/kotlin/dev/kioba/anchor/`
(same package, zero import changes), deleted the `anchor-internal` module,
removed it from `settings.gradle.kts`, `anchor/build.gradle.kts`,
`anchor-test/build.gradle.kts`, and `.github/workflows/package-publish.yml`.
`rootProject.name = "anchorS"` rename (B2) was intentionally left untouched.

**Verify**: `./gradlew build` — one pre-existing failure,
`SubscriptionDslTest.sibling subscription keeps receiving events when
another subscription throws[iosSimulatorArm64]` (native uncaught-exception
abort, signal 6). Confirmed via `git stash` + rerun against unmodified
`master` that this crash is identical and pre-existing — unrelated to the
fold or the iOS-target trim done in the same session. Everything else
(all 4 published modules, remaining targets, 86 total anchor tests) passes.

### Phase A — Investigation (always safe)

**A1**: Run the two git-history commands above. Also:
`git log --oneline --all -- anchor-internal/ | tail -5` and read the commit
messages/PRs that created `anchor-internal` and `anchorS` for stated intent.

**A2**: Read `anchor-internal/build.gradle.kts` — is it published
(`dev.kioba.publish`)? Does it differ from `kmp-library` modules?

**A3**: Test the cycle hypothesis: is there any dependency that would become
circular if the exceptions lived in `:anchor`? Check
`anchor-test`/`anchor-compose`/`convention-plugins` for any dependency *into*
anchor-internal that does NOT also depend on `:anchor`
(`grep -rn "anchorInternal" --include="*.kts" . | grep -v .claude | grep -v build/`).

**A4**: Check `umbrella/build.gradle.kts` and the iOS framework exports for
anchor-internal references.

**A5**: For `anchorS`: search GitHub history (`git log -S "anchorS"`) and
check whether the Xcode project (`iosApp/`) references the root project name
anywhere (`grep -rn "anchorS" --include="*.pbxproj" --include="*.swift" --include="*.kts" --include="*.properties" . | grep -v .claude`).

**A6**: Append an "## Investigation results" section to THIS file with:
creation rationale found (or "none found"), cycle analysis verdict, umbrella/
framework impact list, anchorS origin, and a GO/NO-GO recommendation for each
of the two changes. **STOP here and report.**

### Phase B — Execution (only after maintainer GO)

**B1** (if GO on fold): move `AnchorExceptions.kt` into
`anchor/src/commonMain/kotlin/dev/kioba/anchor/` (same package — zero import
changes anywhere), delete the `anchor-internal` module directory, remove
`include(":anchor-internal")` from `settings.gradle.kts`, remove
`api(projects.anchorInternal)` from `anchor` and `anchor-test` build files,
update umbrella exports if A4 found any, update CLAUDE.md's module diagram.
Note in the PR description: consumers that pinned
`dev.kioba.anchor:anchor-internal` directly must drop it (transitive users are
unaffected since the classes keep their package and move into `anchor`).

**Verify**: `./gradlew build` → exit 0;
`grep -rn "anchorInternal" --include="*.kts" . | grep -v .claude | grep -v build/` → no matches.

**B2** (if GO on rename): `rootProject.name = "anchor"`; rerun
`./gradlew build` and re-open the IDE project to confirm nothing references
the old name (search build output for "anchorS").

**Verify**: `./gradlew build` → exit 0; `grep -rn "anchorS" --include="*.kts" .` (excluding `.claude/`) → no matches.

## Test plan

No behavioral change intended; full suite is the gate:
`./gradlew build` (runs all module tests) before and after Phase B, identical
results.

## Done criteria

Phase A:
- [ ] "Investigation results" section appended with GO/NO-GO per change
- [ ] `plans/README.md` status row set to BLOCKED (awaiting maintainer decision)

Phase B (post-GO):
- [ ] anchor-internal gone (or documented as intentionally kept, with the
      rationale added to CLAUDE.md)
- [ ] rootProject named per decision
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated to DONE

## STOP conditions

Stop and report back if:

- (Built into the plan: end of Phase A is a mandatory stop.)
- A3 finds a genuine dependency cycle reason — record it, recommend NO-GO,
  and propose documenting the rationale in CLAUDE.md instead.
- A5 shows `anchorS` referenced by the Xcode project or CI in a way a rename
  would break.

## Maintenance notes

- If the fold ships, the next release notes must mention the artifact removal
  prominently (pre-1.0 license to break, but say it loudly).
- The `umbrella` module's undocumented purpose surfaced in the same audit;
  whoever reviews A4's findings should decide whether to document it in
  CLAUDE.md while they're there (one paragraph suffices).
