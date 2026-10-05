# Plan 012: Fix actively-wrong docs — Context Receivers claim and non-suspend Quick Start actions

> **Triage note (2026-09-29, origin/master `0bc430c`)**: SUPERSEDED by plan 028 (docs overhaul, #140), which absorbs every item here; they were still accurate apart from version lines. Do not execute separately.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- README.md docs/ mkdocs.yml`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: docs
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

Two first-contact documentation claims are wrong:

1. **README.md line 7** says the library "leverages Kotlin's modern features
   like Context Receivers" — it does not. The codebase uses function receivers
   (extension functions) and SAM conversions; the repo's own CLAUDE.md
   explicitly corrects this. Users arrive expecting an API shape that doesn't
   exist.
2. **Quick Start action examples omit `suspend`** (e.g.
   `fun CounterAnchor.increment()`), while every real action in the repo is
   `suspend fun`. The non-suspend form compiles only until the user adds
   `post {}`/`effect {}`/`cancellable {}` (all suspend) — then their
   copy-pasted starting point breaks confusingly.

Stale docs that are actively wrong are worse than missing docs.

## Current state

- `README.md:7`:

  > **Anchor** is a lightweight, type-safe state management architecture ...
  > It leverages Kotlin's modern features like Context Receivers and SAM
  > conversions to provide a clean, expressive, and powerful DSL.

- `README.md` Quick Start ("### 2. Define Actions", around lines 54-62):

  ```kotlin
  fun CounterAnchor.increment() {
      reduce { copy(count = count + 1) }
  }

  fun CounterAnchor.decrement() {
  ...
  ```

- The real convention — `features/counter/src/commonMain/kotlin/dev/kioba/anchor/features/counter/data/CounterEffects.kt`
  uses `suspend fun CounterAnchor.increment()`; CLAUDE.md's "Function
  Receivers (Not Context Receivers)" section documents the receiver reality.
- `docs/index.md` mirrors the README's Quick Start and framing — audit it for
  the same two errors (`grep -n "Context Receiver" docs/*.md README.md` and
  read the Quick Start blocks).
- CI gates on docs:
  - `pr_check.yml` `version_check` — README/mkdocs/CLAUDE.md version strings
    must stay consistent. **Do not touch version numbers.**
  - `pr_check.yml` `llms_check` — `docs/llms-full.txt` must equal the output
    of `bash scripts/generate-llms-full.sh`. **Regenerate after any docs/ edit.**

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Find all instances | `grep -rn "Context Receiver" README.md docs/ AGENTS.md CLAUDE.md` | (worklist) |
| llms regen | `bash scripts/generate-llms-full.sh` | exit 0 |
| llms gate locally | `diff <(git show HEAD:docs/llms-full.txt) docs/llms-full.txt` | shows your intended changes only |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `README.md`, `docs/index.md`, and any other `docs/*.md` the greps surface
- `docs/llms-full.txt` (regenerated, never hand-edited)
- `AGENTS.md` (only if it repeats the wrong claims)

**Out of scope**:
- Version strings anywhere (CI-gated; the 0.1.5-vs-0.1.6 split is intentional:
  docs reference the last *published* version).
- `CLAUDE.md`'s correct description.
- Kotlin sources.

## Git workflow

- Branch: `docs/fix-receiver-claim-and-suspend`
- Commit style: gitmoji, e.g. `📝 Fix Context Receivers claim and add suspend to Quick Start actions`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Fix the framing claim

In `README.md:7` (and any `docs/` mirror), replace "Context Receivers and SAM
conversions" with wording that matches reality, e.g.:

> It leverages Kotlin's receiver-based extension functions and SAM conversions
> to provide a clean, expressive, and powerful DSL.

**Verify**: `grep -rn "Context Receiver" README.md docs/ AGENTS.md` → no
matches (CLAUDE.md's mention is the *correction* — leave it, but reread it:
its "Despite the README mentioning context receivers" sentence is now stale
too; update that sentence to no longer reference a README error).

### Step 2: Add suspend to every action example

In the README and `docs/index.md` Quick Start blocks, prefix action
definitions with `suspend` (`suspend fun CounterAnchor.increment()`, etc.).
Sweep the rest of `docs/` for non-suspend action definitions:
`grep -rn "^fun [A-Z][A-Za-z]*Anchor\.\| fun [A-Z][A-Za-z]*Anchor\." docs/ README.md` and fix actions (factory functions like
`fun RememberAnchorScope.counterAnchor()` are correctly non-suspend — do NOT
touch those).

**Verify**: every action example matches the convention in
`features/counter/.../CounterEffects.kt`.

### Step 3: Regenerate the llms snapshot

`bash scripts/generate-llms-full.sh`, commit the changed `docs/llms-full.txt`.

**Verify**: re-running the script changes nothing further
(`git status` clean of `docs/llms-full.txt` after the second run).

### Step 4: Build

**Verify**: `./gradlew build` → exit 0 (docs changes can't break it, but the
gate scripts run in CI — this is the cheap local proxy).

## Test plan

Documentation plan — the greps in steps 1-2 plus the llms regeneration
idempotency check in step 3 are the verification.

## Done criteria

- [ ] No "Context Receiver" claims outside CLAUDE.md's (updated) note
- [ ] All doc action examples are `suspend fun`
- [ ] `docs/llms-full.txt` regenerated and stable
- [ ] No version strings changed (`git diff` shows none)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- `scripts/generate-llms-full.sh` fails or is non-idempotent on a clean tree
  (report before committing any docs change).
- The Quick Start examples differ structurally from the excerpt (README has
  been rewritten — re-audit rather than pattern-match).

## Maintenance notes

- ROADMAP #140 (documentation overhaul) supersedes this eventually; this plan
  is the minimum correction so current docs stop lying. Don't expand scope.
- The untracked `ROADMAP.md` at repo root says "Current published version:
  0.1.6" while docs say 0.1.5 — the maintainer should reconcile that line when
  committing ROADMAP.md (out of scope here; noting for the reviewer).
