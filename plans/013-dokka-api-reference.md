# Plan 013: Generate and publish Dokka API reference for the published modules

> **Triage note (2026-09-29, origin/master `0bc430c`)**: The module list still names `anchor-internal`, which was folded into `anchor` in 09a5864, so drop it. Plan 029 produces a package map this plan can reuse. `anchor-test` has no KDoc yet (per the #140 verifier).

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- build.gradle.kts gradle/libs.versions.toml .github/workflows/publish_docs.yml mkdocs.yml`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P3
- **Effort**: M
- **Risk**: LOW
- **Depends on**: none
- **Category**: dx
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

`dev.kioba.anchor:{anchor,anchor-compose,anchor-test,anchor-internal}` are
published to Maven Central with rich KDoc, but no API reference is generated
anywhere — users must read sources. The docs site (mkdocs →
kioba.github.io/anchor) has hand-written guides but no reference layer. This
plan adds Dokka HTML generation aggregated across the published modules and
wires it into the docs-publishing workflow, supporting ROADMAP #140
(documentation overhaul).

## Current state

- Kotlin `2.3.21` (`gradle/libs.versions.toml:15`) → use Dokka 2.x
  (`org.jetbrains.dokka` Gradle plugin, V2 mode is the default in Dokka 2.x).
- Published modules (each applies `id("dev.kioba.publish")`): `anchor`,
  `anchor-compose`, `anchor-test`, `anchor-internal`.
- No `dokka` string anywhere:
  `grep -rni dokka --include="*.kts" --include="*.toml" . | grep -v .claude` → empty.
- Root `build.gradle.kts` exists; convention plugins live in
  `convention-plugins/` (`dev.kioba.kmp-library.gradle.kts`,
  `dev.kioba.publish.gradle.kts`) as an included build whose plugin classpath
  is declared in `convention-plugins/build.gradle.kts`.
- Docs site: `mkdocs.yml` + `docs/`, deployed by
  `.github/workflows/publish_docs.yml`. **Read both before step 4** — this
  plan was written without their full contents; the integration step adapts to
  what is actually there.
- CI gate: `docs/llms-full.txt` must match `bash scripts/generate-llms-full.sh`
  output if you touch `docs/*.md`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Generate (aggregate) | `./gradlew :dokkaGeneratePublicationHtml` (Dokka 2 aggregate task; exact name per Dokka docs once applied) | exit 0, HTML under `build/dokka/` |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `gradle/libs.versions.toml` (dokka version + plugin alias)
- Root `build.gradle.kts` (aggregation) and the four published modules'
  `build.gradle.kts` (apply plugin) — OR via `dev.kioba.publish` convention
  plugin if cleaner (see step 2)
- `.github/workflows/publish_docs.yml` (add generation + publish of the
  reference alongside mkdocs output)
- `mkdocs.yml` / `docs/` (a nav link to the reference, optional)

**Out of scope**:
- KDoc content edits (coverage is already good; gaps are a future pass).
- `features/*`, `androidApp`, `umbrella` (not published API).
- Changing the mkdocs theme/structure.

## Git workflow

- Branch: `docs/dokka-api-reference`
- Commit style: gitmoji, e.g. `📝 Add Dokka API reference generation`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Add Dokka to the version catalog

In `gradle/libs.versions.toml`: `dokka = "2.1.0"` (use the latest 2.x — check
https://github.com/Kotlin/dokka/releases; do not use 1.9.x) and a plugin entry
`dokka = { id = "org.jetbrains.dokka", version.ref = "dokka" }`.

**Verify**: `./gradlew help` → exit 0 (catalog parses).

### Step 2: Apply to published modules + aggregate at root

Apply `alias(libs.plugins.dokka)` in the four published modules and in the
root `build.gradle.kts`; in the root, add the Dokka aggregation dependencies
(`dependencies { dokka(project(":anchor")) ... }` per Dokka 2.x aggregation
docs) so one task produces a combined site. Configure module-level source
links to GitHub (`remoteUrl = https://github.com/kioba/anchor/tree/master`) if
straightforward; skip if it fights the convention-plugin structure.

Note: applying via the `dev.kioba.publish` convention plugin would be tidier,
but requires adding the Dokka plugin to `convention-plugins/build.gradle.kts`'s
classpath; choose direct application in each module unless you find the
convention route equally simple. State your choice in the report.

**Verify**: the aggregate Dokka task runs → exit 0; open output dir, confirm
all four modules appear.

### Step 3: Exclude internals

`anchor-internal`'s two exception classes are public API — include them.
But verify `@InternalAnchorApi`-annotated members (if plan 007 landed) and
`internal`/`@PublishedApi` members are not rendered (Dokka skips non-public by
default; spot-check `dev.kioba.anchor.internal` package pages — if
`safeExecute` appears, configure `suppressedFiles`/visibility settings to drop
the `internal` package from `:anchor` docs).

**Verify**: generated site has no `dev.kioba.anchor.internal` member pages
(or they are clearly opt-in-marked), and `RememberAnchor`, `HandleSignal`,
`runAnchorTest` pages exist.

### Step 4: Wire into the docs workflow

Read `.github/workflows/publish_docs.yml`. Add a step that runs the aggregate
Dokka task and copies its output into the deployed site under `api/` (the
mechanics depend on whether the workflow uses `mkdocs gh-deploy` or an
artifact upload — match its existing structure). Add an `API Reference` link
in `mkdocs.yml` nav pointing at `api/index.html` if the nav structure permits
an external-style link.

**Verify**: workflow YAML parses (`python3 -c "import yaml,sys; yaml.safe_load(open('.github/workflows/publish_docs.yml'))"`) and, if you edited
`docs/*.md`, `bash scripts/generate-llms-full.sh` then commit the regenerated
file.

### Step 5: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

Generation IS the test: aggregate task exit 0, four modules present, no
internal package leakage, workflow YAML valid.

## Done criteria

- [ ] Aggregate Dokka task generates HTML for the 4 published modules
- [ ] No `dev.kioba.anchor.internal` pages (post-007 check)
- [ ] `publish_docs.yml` includes the generation + deploy of `api/`
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- Dokka 2.x is incompatible with Kotlin 2.3.21 / AGP 9.2 in this build
  (classpath or task-configuration errors after a reasonable fix attempt) —
  report versions tried.
- The KMP setup (android-multiplatformLibrary plugin) breaks Dokka's source-set
  analysis — report the error; per-target docs may need advisor input.
- `publish_docs.yml` has a structure that makes the `api/` copy ambiguous
  (e.g. third-party deploy action with fixed dirs) — propose, don't improvise.

## Maintenance notes

- Dokka adds noticeable build time; it is wired into the docs workflow, NOT
  into `./gradlew build`/PR CI — keep it that way.
- When modules gain/lose published status, the root aggregation list must be
  updated — note this in CLAUDE.md's publishing section in a follow-up.
