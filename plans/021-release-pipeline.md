# Plan 021: Make "cut release" actually publish, release after publish with CHANGELOG notes, and keep AGENTS.md in sync

> **Executor instructions**: This is an INVESTIGATE-THEN-ACT plan with a hard
> decision gate. Complete Phase A (investigation), write the findings into
> this file under "Investigation results", and STOP for maintainer review
> before executing Phase B. Do not perform Phase B in the same run unless the
> operator explicitly pre-authorized it. Follow every step, run every
> verification command, and confirm the expected result before moving on. If
> anything in the "STOP conditions" section occurs, stop and report. Do not
> improvise. When done (either phase), update the status row for this plan in
> `plans/README.md`, unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0bc430c..HEAD -- .github/workflows/ AGENTS.md scripts/ CHANGELOG.md`
> Expected drift: PR #268 changes `android-actions/setup-android@v3` → `@v4`
> in `package-publish.yml`, `pr_check.yml` and `ios_check.yml`. That is a
> prerequisite, not a mismatch. For any other change to an in-scope file,
> compare the "Current state" excerpts against the live files before
> proceeding. On a mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M (A: S, B: M)
- **Risk**: MED (release pipeline; the first real exercise is the 0.1.9 release itself)
- **Depends on**: PR #268 merged (setup-android v4; the publish job fails on v3). Companion plan
  `plans/027-release-0-1-9-migration.md` must land before the maintainer *cuts* 0.1.9,
  because this plan makes `cut release` refuse to tag without a CHANGELOG section. That plan does
  not block executing this one.
- **Category**: ci / release
- **Planned at**: commit `0bc430c`, 2026-09-29
- **Spec**: `plans/specs/021-release-pipeline.md`

## Why this matters

The release process was rebuilt on 2026-07-07 (`0c0bb9d`) so that a maintainer-run `cut release`
workflow pushes a tag, and the tag push triggers `publish package`. That hand-off can't work. The tag
is pushed with the default `GITHUB_TOKEN`, and GitHub documents that *"events triggered by the
`GITHUB_TOKEN` will not create a new workflow run"* (exceptions: `workflow_dispatch`,
`repository_dispatch`, approval-gated `pull_request`). `cut release` has never been run, so this
hasn't surfaced yet. Running it for 0.1.9 would create the tag and a GitHub release announcing
0.1.9 while nothing reaches Maven Central.

Three smaller defects ride along:
- The GitHub release is created *before* publishing.
- Auto-generated notes list only merged PRs, so the three direct-pushed 0.1.9 commits (the anchor-internal
  fold, the iosX64 drop, the native containment fix) would be absent from the notes.
- `AGENTS.md` has said "Published version: 0.1.5" through three releases because the bump-step
  sed never matches it.

## Current state

- `.github/workflows/cut-release.yml:1-17` (trigger, permissions, checkout with default token):

  ```yaml
  name: 'cut release'

  on:
    workflow_dispatch:

  jobs:
    tag:
      name: 'tag version and create GitHub release'
      runs-on: ubuntu-latest
      permissions:
        contents: write

      steps:
        - name: checkout project
          uses: actions/checkout@v4
          with:
            fetch-depth: 0
  ```

- `.github/workflows/cut-release.yml:34-41`:

  ```yaml
        - name: Create tag and GitHub release
          env:
            GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
          run: |
            VERSION="${{ steps.version.outputs.version }}"
            git tag "$VERSION"
            git push origin "$VERSION"
            gh release create "$VERSION" --generate-notes --title "v$VERSION"
  ```

- `actions/checkout` v4 `action.yml`: `token` → `default: ${{ github.token }}` (line 24);
  `persist-credentials` → `default: true` (line 54). So the `git push` above authenticates as
  `GITHUB_TOKEN`.
- `.github/workflows/package-publish.yml:1-6`, the only trigger:

  ```yaml
  name: 'publish package'

  on:
    push:
      tags:
        - '[0-9]+.[0-9]+.[0-9]+'
  ```

- `.github/workflows/package-publish.yml:13-15`: `permissions: contents: write, pull-requests: write`.
  Lines 78-88 are "Publish to Maven Central", and lines 90-116 are "Bump patch version for next development
  iteration", which starts with `VERSION="${GITHUB_REF_NAME}"` (line 94). Line 108:

  ```bash
  sed -i '' "s|Published version: [0-9]*\.[0-9]*\.[0-9]*|Published version: ${VERSION}|" AGENTS.md
  ```

- `AGENTS.md:7`: `- **Published version**: 0.1.5`. The text has `**` between "version" and ":", so line 108 never
  matches. Verified by running lines 107-108 with BSD sed on a copy (VERSION=0.1.9): only lines 14-16
  changed.
- `.github/workflows/pr_check.yml:19-40` (`version_check`) compares README, mkdocs and CLAUDE.md only.
  AGENTS.md isn't checked.
- `gh run list --workflow cut-release.yml` → empty (never run). Repo secrets: `GPG_PASSWORD`,
  `GPG_SECRET_KEY`, `MAVEN_CENTRAL_PASSWORD`, `MAVEN_CENTRAL_USERNAME` (no PAT or App token).
- No `CHANGELOG.md` and no `scripts/release-notes.sh` exist. `scripts/` contains only
  `generate-llms-full.sh`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Prereq PR state | `gh pr view 268 --repo kioba/anchor --json state,mergedAt` | `"state":"MERGED"` |
| cut-release history | `gh run list --repo kioba/anchor --workflow cut-release.yml` | empty (or see STOP) |
| Secret names | `gh api repos/kioba/anchor/actions/secrets --jq '.secrets[].name'` | the 4 names above |
| Notes script test | see Step B3 | `PASS` lines |
| AGENTS gate (local) | see Step B1 | `PASS` after fix |
| Workflow lint | `actionlint .github/workflows/*.yml` (or the docker form in B6) | no errors |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- Phase A: this plan file (append "Investigation results"). No other changes.
- Phase B: `.github/workflows/cut-release.yml`, `.github/workflows/package-publish.yml`,
  `.github/workflows/pr_check.yml` (version_check job only), `AGENTS.md` (line 7 only),
  `scripts/release-notes.sh` (create).

**Out of scope**:
- `CHANGELOG.md` content (companion plan 027). Don't create CHANGELOG.md here.
- Gradle publish configuration, `convention-plugins/`, signing, and Maven Central settings.
- `ios_check.yml`, `dependabot*.yml`, `publish_docs.yml`.
- Creating secrets, PATs or GitHub Apps.
- **Running `cut release`, pushing tags, or dispatching `publish package`.** Never do these. The
  release is the maintainer's action.

## Git workflow

- Branch: `ci/release-pipeline-dispatch`
- Commit style: gitmoji, e.g. `💚 Dispatch publish from cut release and create the release after publishing`
  and `🔧 Keep AGENTS.md published version in sync`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A: Investigation (always safe)

**A1**: Run the drift check. Confirm PR #268 is merged
(`gh pr view 268 --repo kioba/anchor --json state,mergedAt`).

**Verify**: drift limited to the setup-android v4 bump, and #268 `MERGED`. If #268 isn't merged, STOP.

**A2**: Re-establish the defect. Run `gh run list --repo kioba/anchor --workflow cut-release.yml` and
`gh run list --repo kioba/anchor --workflow package-publish.yml --limit 5`. Re-read
`cut-release.yml` and confirm the checkout still has no `token:` input.

**Verify**: no cut-release runs, *or* a cut-release run exists and **no** publish run with
`event=push` followed it for the same tag. If a cut-release run was followed by a tag-push-triggered
publish run, STOP. The analysis is wrong and must be revisited.

**A3**: Reproduce the AGENTS.md sed miss on a scratch copy (never edit the tree in Phase A):

```bash
T=$(mktemp -d); cp AGENTS.md "$T/A.md"; VERSION=9.9.9; GROUP_ID=dev.kioba.anchor
sed -i '' -E "s|(${GROUP_ID}:anchor[a-z-]*):[0-9]+\.[0-9]+\.[0-9]+|\1:${VERSION}|g" "$T/A.md"
sed -i '' "s|Published version: [0-9]*\.[0-9]*\.[0-9]*|Published version: ${VERSION}|" "$T/A.md"
diff AGENTS.md "$T/A.md"
```

**Verify**: the diff shows only the three `dev.kioba.anchor:anchor…:9.9.9` lines and **not** line 7.

**A4**: Append `## Investigation results` to this file: A1-A3 outcomes, secret names, and the four maintainer
questions from the spec (Q1 dispatch trigger, Q2 release-after-publish, Q3 PAT alternative, Q4 #268). Set this
plan's row in `plans/README.md` to BLOCKED (awaiting maintainer GO). **STOP here and report.**

### Phase B: Execution (only after maintainer GO; if GO chose the PAT alternative, do only B1-B3 + B6-B8 and replace B4-B5 with the maintainer's instruction)

### Step B1: AGENTS.md CI gate, failing first

In `.github/workflows/pr_check.yml` `version_check`, after the `README_VERSION=` line (currently line 24),
add:

```bash
          AGENTS_VERSION=$(sed -n -E 's/.*\*\*Published version\*\*: ([0-9]+\.[0-9]+\.[0-9]+).*/\1/p' AGENTS.md | head -1)
          AGENTS_DEP_VERSION=$(grep -oP "${GROUP_ID}:anchor:\K[0-9]+\.[0-9]+\.[0-9]+" AGENTS.md | head -1)
```

and, after the CLAUDE.md comparison block, add the two comparisons:

```bash
          if [ "$AGENTS_VERSION" != "$DOCS_VERSION" ]; then
            echo "::error file=AGENTS.md::Published version $AGENTS_VERSION does not match mkdocs.yml ($DOCS_VERSION)"
            ERRORS=$((ERRORS+1))
          fi

          if [ "$AGENTS_DEP_VERSION" != "$DOCS_VERSION" ]; then
            echo "::error file=AGENTS.md::Dependency version $AGENTS_DEP_VERSION does not match mkdocs.yml ($DOCS_VERSION)"
            ERRORS=$((ERRORS+1))
          fi
```

Also add `AGENTS.md` to the summary error message on the `exit 1` line. Then run the local equivalent
(BSD- and GNU-portable):

```bash
DOCS=$(sed -n -E 's/.*version: "([0-9]+\.[0-9]+\.[0-9]+)".*/\1/p' mkdocs.yml | head -1)
AG=$(sed -n -E 's/.*\*\*Published version\*\*: ([0-9]+\.[0-9]+\.[0-9]+).*/\1/p' AGENTS.md | head -1)
[ "$AG" = "$DOCS" ] && echo PASS || echo "FAIL: AGENTS=$AG mkdocs=$DOCS"
```

**Verify**: prints `FAIL: AGENTS=0.1.5 mkdocs=0.1.8` (the gate catches the existing drift). If mkdocs
already says something other than 0.1.8, a release happened since planning: use that value throughout.

### Step B2: Fix the bump-step sed and AGENTS.md, making B1 pass

1. `package-publish.yml:108` → replace with:

   ```bash
   sed -i '' "s|\*\*Published version\*\*: [0-9]*\.[0-9]*\.[0-9]*|**Published version**: ${VERSION}|" AGENTS.md
   ```

2. `AGENTS.md:7` → `- **Published version**: 0.1.8` (the currently published version; don't touch
   line 8's platform list, which companion plan B owns).

**Verify**:
- The B1 local check prints `PASS`.
- Re-run the A3 script with the **new** line-108 sed. The diff now shows line 7 → `9.9.9` **and** the three
  dependency lines.

### Step B3: `scripts/release-notes.sh`, test first

Write the check first (scratch, not committed):

```bash
T=$(mktemp -d)
printf '# Changelog\n\n## [0.1.9] - 2026-10-01\n\n### Breaking\n- foo\n\n## [0.1.8]\n- bar\n' > "$T/CH.md"
OUT=$(bash scripts/release-notes.sh 0.1.9 "$T/CH.md") && printf '%s' "$OUT" | grep -q '^- foo$' && ! printf '%s' "$OUT" | grep -q 'bar' && echo PASS-extract || echo FAIL-extract
bash scripts/release-notes.sh 0.2.0 "$T/CH.md" >/dev/null 2>&1 && echo FAIL-missing || echo PASS-missing
bash scripts/release-notes.sh 0.1.1 "$T/CH.md" >/dev/null 2>&1 && echo FAIL-prefix || echo PASS-prefix
```

Run it now. **Verify**: `FAIL-extract` (the script doesn't exist yet).

Then create `scripts/release-notes.sh` (make it executable, and match `generate-llms-full.sh`'s header style):

```bash
#!/usr/bin/env bash
set -euo pipefail

# Prints the body of the "## [<version>]" section of CHANGELOG.md (Keep a
# Changelog format) for use as GitHub release notes. Exits non-zero when the
# section is missing or empty so release workflows fail before tagging.

VERSION="${1:?usage: release-notes.sh <version> [changelog-file]}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${2:-$REPO_ROOT/CHANGELOG.md}"

if [ ! -f "$FILE" ]; then
  echo "Error: $FILE not found" >&2
  exit 1
fi

BODY=$(awk -v v="$VERSION" '
  index($0, "## [" v "]") == 1 { found = 1; next }
  found && /^## / { exit }
  found { print }
' "$FILE")

if [ -z "$(printf '%s' "$BODY" | tr -d '[:space:]')" ]; then
  echo "Error: no non-empty '## [$VERSION]' section in $FILE" >&2
  exit 1
fi

printf '%s\n' "$BODY"
```

**Verify**: re-run the check → `PASS-extract`, `PASS-missing`, `PASS-prefix`. Also
`bash scripts/release-notes.sh 0.1.9` (no CHANGELOG.md in the tree yet) exits 1 with
`Error: …CHANGELOG.md not found`.

### Step B4: `package-publish.yml`: guarded dispatch trigger, concurrency, release after publish

1. Trigger block (lines 3-6) becomes:

   ```yaml
   on:
     push:
       tags:
         - '[0-9]+.[0-9]+.[0-9]+'
     # Dispatched by cut-release.yml: a tag pushed with GITHUB_TOKEN does not
     # trigger push workflows. Also usable to re-run a failed publish on a tag.
     workflow_dispatch:

   concurrency:
     group: publish-${{ github.ref_name }}
     cancel-in-progress: false
   ```

2. Immediately after the `checkout project` step, insert:

   ```yaml
         - name: Verify ref is a release tag matching POM_VERSION
           run: |
             if [ "${GITHUB_REF_TYPE}" != "tag" ]; then
               echo "::error::publish package must run on a tag ref (got ${GITHUB_REF})"
               exit 1
             fi
             POM_VERSION=$(grep '^POM_VERSION=' gradle.properties | cut -d'=' -f2)
             if [ "${GITHUB_REF_NAME}" != "${POM_VERSION}" ]; then
               echo "::error::Tag ${GITHUB_REF_NAME} does not match POM_VERSION=${POM_VERSION}"
               exit 1
             fi
             bash scripts/release-notes.sh "${GITHUB_REF_NAME}" > /dev/null
   ```

3. Between "Publish to Maven Central" and "Bump patch version…", insert:

   ```yaml
         - name: Create GitHub release
           env:
             GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
           run: |
             VERSION="${GITHUB_REF_NAME}"
             if gh release view "$VERSION" >/dev/null 2>&1; then
               echo "Release $VERSION already exists, skipping"
             else
               NOTES=$(bash scripts/release-notes.sh "$VERSION")
               gh release create "$VERSION" --verify-tag --title "v$VERSION" --generate-notes --notes "$NOTES"
             fi
   ```

**Verify**: `git diff .github/workflows/package-publish.yml` shows only these three hunks plus the B2 line-108
change. The existing steps are byte-identical otherwise.

### Step B5: `cut-release.yml`: validate notes, tag, dispatch (no release creation)

1. Job `name:` → `'tag version and dispatch publish'`. Permissions add `actions: write`:

   ```yaml
       permissions:
         contents: write
         actions: write
   ```

2. After "Check tag does not already exist", insert:

   ```yaml
         - name: Check CHANGELOG has a section for this version
           run: bash scripts/release-notes.sh "${{ steps.version.outputs.version }}" > /dev/null
   ```

3. Replace the "Create tag and GitHub release" step (lines 34-41) with:

   ```yaml
         - name: Create tag and dispatch publish
           env:
             GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
           run: |
             VERSION="${{ steps.version.outputs.version }}"
             git tag "$VERSION"
             git push origin "$VERSION"
             # Pushing a tag with GITHUB_TOKEN does not start push-triggered
             # workflows; workflow_dispatch always does. package-publish.yml
             # creates the GitHub release after Maven Central succeeds.
             gh workflow run package-publish.yml --ref "$VERSION"
             echo "Dispatched 'publish package' for $VERSION"
   ```

**Verify**: `grep -n 'gh release create' .github/workflows/cut-release.yml` → no match;
`grep -n 'release-notes.sh' .github/workflows/cut-release.yml` shows a line number **smaller** than
`grep -n 'git tag' …`.

### Step B6: Lint the workflows

Run `actionlint .github/workflows/cut-release.yml .github/workflows/package-publish.yml .github/workflows/pr_check.yml`.
If `actionlint` isn't installed and Docker is available, run
`docker run --rm -v "$PWD:/repo" -w /repo rhysd/actionlint:latest .github/workflows/cut-release.yml .github/workflows/package-publish.yml .github/workflows/pr_check.yml`.
If neither is available, parse each file with `ruby -ryaml -e 'ARGV.each { |f| YAML.load_file(f) }' <files>`
and say in your report that actionlint was not run. Don't install tools without the operator's OK.

**Verify**: no errors. Treat shellcheck-level *info* findings as optional, but *errors* must be fixed.

### Step B7: Full build (convention gate)

**Verify**: `./gradlew build` → exit 0. Nothing Gradle-facing changed, so a failure here is pre-existing:
report it and don't fix it.

### Step B8: Hand-off checklist for the maintainer (write into your report; do NOT execute)

1. Merge order: #268 → this plan's PR → 027's PR (CHANGELOG + migration).
2. Actions → "cut release" → Run workflow on `master`.
3. Confirm a "publish package" run with event `workflow_dispatch` on ref `0.1.9` started, publishing
   succeeded, a GitHub release `v0.1.9` exists whose body begins with the CHANGELOG section, and a
   "🔖 Bump version to 0.1.10" PR was opened (its checks will wait for approval, since the PR was created by `GITHUB_TOKEN`).
4. Confirm `https://repo1.maven.org/maven2/dev/kioba/anchor/anchor/maven-metadata.xml` lists 0.1.9
   (Central sync can lag).
5. Fallback if the dispatch fails: Actions → "publish package" → Run workflow → *Use workflow from:* tag `0.1.9`.

## Test plan

Test-first order, all local and none needing a release:
1. B1: the AGENTS gate is written first and **fails** on the current tree (`FAIL: AGENTS=0.1.5 mkdocs=0.1.8`).
2. A3/B2: the bump-sed simulation **fails** to touch line 7 before the fix, and touches it after.
3. B3: the release-notes fixture check **fails** before the script exists and passes after (extract, missing
   version, prefix-collision `0.1.1` vs `0.1.10`-style headings).
4. B6: workflow lint.
The dispatch hand-off itself can only be exercised by a real release (B8). That is the accepted residual risk,
mitigated by the tag/POM guard and the manual re-run path.

## Done criteria

Phase A:
- [ ] "Investigation results" appended (A1-A3 outcomes, secret names, Q1-Q4)
- [ ] `plans/README.md` row set to BLOCKED (awaiting maintainer GO)

Phase B:
- [ ] `pr_check.yml` version_check gates AGENTS.md (published version + dependency coordinate)
- [ ] `package-publish.yml:108` sed matches the bold label; `AGENTS.md:7` reads 0.1.8
- [ ] `scripts/release-notes.sh` exists, is executable, and passes the B3 checks
- [ ] `package-publish.yml`: `workflow_dispatch`, `concurrency`, the tag/POM guard, and a release step after publish
- [ ] `cut-release.yml`: `actions: write`, the notes check before tagging, dispatch instead of `gh release create`
- [ ] Workflow lint clean (or the fallback reported); `./gradlew build` exits 0
- [ ] Hand-off checklist (B8) included in the report
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- (Built into the plan) the end of Phase A is a mandatory stop.
- PR #268 isn't merged (A1).
- A2 shows a tag pushed by `cut release` *did* trigger a push-event publish run.
- A secret usable as a release token has appeared (the maintainer may prefer spec Q3's PAT path).
- Any step appears to require changing Gradle/publishing configuration or creating secrets.
- `actionlint` reports an error you can't resolve within the in-scope lines.
- You're tempted to test by running `cut release`, pushing a tag, or dispatching `publish package`. Never do
  this. It publishes irreversibly to Maven Central.

## Maintenance notes

- **Invariant to preserve** (from the 2026-07-07 quota incident): publishing happens only on a deliberate
  maintainer action. Never add `schedule:` or merge-triggered publishing. `workflow_dispatch` is
  guarded to tag refs for exactly this reason.
- The `GITHUB_TOKEN` rule bites any future chaining (for example a workflow reacting to the bump PR). Use
  `workflow_dispatch` or a real token, and don't rely on push/release events emitted by another workflow.
- If a PAT/App token is introduced later, a cut-release tag push would *also* fire the push trigger. The
  `concurrency` group plus Central's duplicate-version rejection make that safe, but remove the explicit dispatch
  then.
- CHANGELOG convention consumed by the script: `## [x.y.z] - YYYY-MM-DD`. Section bodies can use `###`
  subsections freely. Only a line starting with `## ` ends a section.
- Reviewer: check that `--generate-notes` still computes the previous-release base correctly once the
  release is created by `package-publish.yml` rather than `cut-release.yml` (it uses the previous *release*,
  and v0.1.8 exists).
