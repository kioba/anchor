# Spec: #237 (release train): the release pipeline can't publish 0.1.9 as wired

- **Issue**: https://github.com/kioba/anchor/issues/237 (roadmap tracker; this is the "ship 0.1.9" item)
- **Verdict**: CONFIRMED (static evidence plus GitHub's documented token semantics; the workflow has never run)
- **Severity**: P1. Running "cut release" will create a tag and a GitHub release for 0.1.9 but will
  **not** publish to Maven Central, so consumers are told about a version that doesn't resolve. The workaround
  is to push the tag from a local clone.
- **Companion spec**: `plans/specs/027-release-0-1-9-migration.md` (content: CHANGELOG,
  migration, docs; also holds the full #237 reconciliation table and proposed tracker text)
- **Plan**: `plans/021-release-pipeline.md`
- **Written**: 2026-09-29 against origin/master `0bc430c`

## Problem statement (evidence)

1. **The tag push from `cut release` can't trigger `publish package`.**
   - `package-publish.yml:3-6` triggers only on `push: tags: ['[0-9]+.[0-9]+.[0-9]+']`.
   - `cut-release.yml:14-17` checks out with `actions/checkout@v4` and no `token:`, and `cut-release.yml:39-40` runs
     `git tag "$VERSION"` and `git push origin "$VERSION"`. `actions/checkout` v4 `action.yml` defaults `token`
     to `${{ github.token }}` (line 24) and `persist-credentials` to `true` (line 54), so the push is
     authenticated as `GITHUB_TOKEN`.
   - GitHub docs ("Triggering a workflow from a workflow"): *"When using the repository's `GITHUB_TOKEN`
     to perform tasks, events triggered by the `GITHUB_TOKEN` will not create a new workflow run"*. The only
     exceptions are `workflow_dispatch`, `repository_dispatch`, and approval-gated `pull_request`. A tag push is
     a `push` event.
   - `gh run list --workflow cut-release.yml` returns nothing, so the workflow has never run since it was added in
     `0c0bb9d` (2026-07-07). Every release up to 0.1.8 came from the old weekly cron. The tag-triggered path is
     untested.
   - Repo secrets (`gh api repos/kioba/anchor/actions/secrets`) are only `GPG_PASSWORD`, `GPG_SECRET_KEY`,
     `MAVEN_CENTRAL_PASSWORD` and `MAVEN_CENTRAL_USERNAME`. No PAT or GitHub App credential exists that could replace
     `GITHUB_TOKEN`.
2. **The GitHub release is created before, and independently of, publishing.** `cut-release.yml:41` runs
   `gh release create "$VERSION" --generate-notes …` in the same step as the tag push. If publishing fails or
   never starts (point 1), the release still announces the version.
3. **Auto-generated notes will omit the most consumer-relevant 0.1.9 changes.** GitHub docs: auto-generated
   notes *"include a list of merged pull requests, a list of contributors to the release, and a link to a full
   changelog."* `gh api repos/kioba/anchor/commits/<sha>/pulls` returns `[]` for `09a5864` (anchor-internal fold
   and iosX64 drop), `687d4e5` (native containment fix) and `0c0bb9d`: they were pushed directly. The v0.1.8
   release body confirms the PR-only format (four PR bullets and a compare link).
4. **`AGENTS.md`'s published version has been frozen at 0.1.5 for three releases.** `AGENTS.md:7` is
   `- **Published version**: 0.1.5`. The post-publish bump step `package-publish.yml:108` runs
   `sed -i '' "s|Published version: [0-9]*…|…|" AGENTS.md`. That pattern needs `Published version: ` but the file
   has `Published version**: `, so it never matches. Simulating lines 107-108 with BSD sed on the current
   `AGENTS.md` (VERSION=0.1.9) changes only lines 14-16 (the dependency coordinates), not line 7. The `pr check`
   `version_check` job (`pr_check.yml:19-40`) compares README, mkdocs and CLAUDE.md only, so nothing catches it.
5. **No guard that the tag equals `POM_VERSION`** in `package-publish.yml`. A manually pushed tag that disagrees
   with `gradle.properties` would publish artifacts whose version differs from the tag.
6. **CI is currently red for an external reason.** `android-actions/setup-android@v3` fails because
   Google removed the `tools` package. `ios check` has failed on schedule since 2026-09-16 (`gh run list
   --workflow ios_check.yml`). PR #268 (open, green) moves all three workflows to v4. `package-publish.yml:47-48`
   uses v3, so a release attempted before #268 merges fails at SDK setup.

## Goals

- One maintainer action ("Run workflow" on `cut release`) results in: tag created, artifacts published to
  Maven Central, GitHub release created **after** a successful publish with curated notes, and the version-bump PR opened.
- Pushing a tag from a local clone still works as a manual fallback.
- Release notes carry the CHANGELOG section for the version, followed by the auto-generated PR list.
- Tracked "published version" strings (README, mkdocs, CLAUDE.md, AGENTS.md) stay in sync and are CI-gated.
- The "maintainer decides when to release" gate stays intact: no schedule, no auto-release on merge.

## Non-goals

- CHANGELOG *content* for 0.1.9 (companion spec B).
- Reducing published file counts further, or changing the Maven Central plugin setup.
- Introducing a PAT or GitHub App credential (considered and rejected below).
- Making `pull_request` checks run automatically on the bot's version-bump PR. With `GITHUB_TOKEN`
  they start in an approval-required state per the same docs page. This is noted, not changed.

## Proposed design

1. **`package-publish.yml` gains a guarded `workflow_dispatch` trigger** alongside `push: tags`. A first
   step fails unless `github.ref` starts with `refs/tags/` and `GITHUB_REF_NAME` equals `POM_VERSION` in
   `gradle.properties` (fixes point 5). Add `concurrency: { group: publish-${{ github.ref_name }},
   cancel-in-progress: false }` so a manual tag push and a dispatch can't publish twice at once.
2. **`cut-release.yml` dispatches the publish explicitly.** After validating (existing tag check, plus the
   CHANGELOG guard below), it creates and pushes the tag, then runs
   `gh workflow run package-publish.yml --ref "$VERSION"`. Per the docs above, `workflow_dispatch` events
   always create runs even from `GITHUB_TOKEN`. The job needs `permissions: actions: write` in addition to
   `contents: write`. `cut-release.yml` no longer creates the GitHub release.
3. **`package-publish.yml` creates the GitHub release after "Publish to Maven Central" succeeds.** It runs
   `gh release create "$VERSION" --verify-tag --title "v$VERSION" --generate-notes --notes "<CHANGELOG section>"`.
   `gh release create --help`: *"Additional release notes can be prepended to automatically generated notes by
   using the `--notes` flag."* The job already has `contents: write` (`package-publish.yml:13-15`).
4. **`scripts/release-notes.sh <version> [file]`** prints the body of the `## [<version>]` section of
   `CHANGELOG.md` (up to the next `## ` heading) and exits non-zero if the section is missing or empty.
   `cut-release.yml` calls it **before** tagging, so it fails fast with no tag pushed. `package-publish.yml` calls it again to
   build the notes.
5. **AGENTS.md sync**: change the bump-step sed at `package-publish.yml:108` to match the bold form
   (`s|\*\*Published version\*\*: [0-9]*\.[0-9]*\.[0-9]*|**Published version**: ${VERSION}|`), set
   `AGENTS.md:7` to the currently published `0.1.8`, and extend `pr_check.yml` `version_check` to compare AGENTS.md's
   published version and its first `dev.kioba.anchor:anchor:` coordinate against mkdocs.

### Alternatives considered

- **Use a PAT or GitHub App token for the tag push** (checkout with `token: ${{ secrets.RELEASE_TOKEN }}`).
  Rejected as the default. It needs a new long-lived credential (none exists), adds rotation and ownership burden, and
  keeps the "release before publish" ordering problem.
- **Turn `package-publish.yml` into a reusable `workflow_call` job invoked by `cut-release`.** Rejected.
  `GITHUB_REF_NAME` inside a called workflow is the caller's ref (`master`), which breaks the bump step's
  `VERSION="${GITHUB_REF_NAME}"` (`package-publish.yml:94`) and needs inputs threaded through. It's a bigger diff for the
  same result.
- **Delete `cut-release.yml` and document "push the tag locally".** Workable, and that path keeps working as the fallback,
  but it loses the POM_VERSION/tag-exists validation that `cut-release` provides. Kept as fallback, not as primary.
- **Keep `--generate-notes` only and hand-edit the release afterwards.** Rejected. It relies on memory, and the notes for
  0.1.9 would be missing the three direct-pushed commits (point 3) the moment the release goes out.
- **Change `AGENTS.md:7` to the plain `Published version: X` form so the existing sed matches.** Equally valid.
  Choosing the sed fix keeps the file's bold-label style consistent with its neighbours (`AGENTS.md:5-8`). Either is
  fine as long as the CI gate covers it.

## Behavior and API changes

No library API or behavior changes; CI and release process only. It's not breaking for consumers.
Maintainer-visible changes:
- `cut release` now fails before tagging if `CHANGELOG.md` has no section for `POM_VERSION`.
- The GitHub release appears only after Maven Central publishing succeeds (it used to be created up front).
- `publish package` can be re-run via "Run workflow" on a tag ref (for example after a transient Central failure), and
  refuses non-tag refs.

## Acceptance criteria

- `bash scripts/release-notes.sh 0.1.9 <fixture>` prints exactly the fixture's 0.1.9 body. For a
  missing version it exits non-zero with a message.
- Running the bump-step sed lines (BSD sed, as on `macos-latest`) against a copy of `AGENTS.md` with
  `VERSION=9.9.9` changes line 7 **and** the three dependency coordinates.
- The extended `version_check` snippet fails on the current `AGENTS.md` (0.1.5 vs 0.1.8) and passes after the fix.
- `actionlint` (or an equivalent YAML lint) reports no errors on the three edited workflows.
- `cut-release.yml` has `actions: write`, calls `release-notes.sh` before `git tag`, dispatches `package-publish.yml`
  with `--ref "$VERSION"`, and no longer calls `gh release create`.
- `package-publish.yml` has the tag/POM guard as its first step, the `concurrency` group, and `gh release create
  --verify-tag` after the publish step.
- `./gradlew build` exits 0 (unchanged; sanity).
- The first real exercise is the 0.1.9 release itself, and the maintainer confirms (plan hand-off checklist) that the publish
  run started from the dispatch and the release body contains the CHANGELOG section.

## Open questions for the maintainer

- **Q1**: OK to add `workflow_dispatch` (tag-guarded) to `publish package`? It re-enables manual re-runs, but
  never on a schedule.
- **Q2**: Is moving GitHub-release creation after publish acceptable? The release would no longer exist while
  publishing is in flight.
- **Q3**: Would you rather create a PAT or App token and keep the current shape? If so, the plan's Phase B is replaced by a
  two-line checkout change plus the notes/AGENTS items.
- **Q4**: Merge PR #268 first? It's a hard prerequisite: plan A rebases on it because both touch
  `package-publish.yml`.

## Relationship to existing plans

- **Complements** `plans/README.md` "Findings considered and rejected" (the doc-version skew is by design). This spec
  keeps that invariant and only adds AGENTS.md to the gate.
- **Consumes** companion spec B's `CHANGELOG.md`. Spec B's release can't publish without this plan.
- **Unrelated to** plan 011 (wrapper checksum) and plan 010 (GitHub Packages consumption repo), although all three touch
  build/CI files. There are no overlapping lines.
