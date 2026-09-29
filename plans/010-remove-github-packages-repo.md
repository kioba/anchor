# Plan 010: Remove the vestigial GitHub Packages repository and its credential fallback

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- settings.gradle.kts CLAUDE.md AGENTS.md`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

Commit `40a85d9` ("💚 Drop GitHub Packages publish and split build from
publish (#232)") removed GitHub Packages *publishing*, but the **consumption**
repository remains wired in `settings.gradle.kts` with a credential fallback to
`System.getenv("USERNAME")` and `System.getenv("TOKEN")`. Problems:

- On Windows, `USERNAME` is always set (the OS username); any generic `TOKEN`
  env var a contributor has for unrelated tooling gets sent as a Maven password
  to `maven.pkg.github.com` on every dependency resolution attempt.
- The repository serves no purpose: all dependencies resolve from `google()`,
  `mavenCentral()`, or the JetBrains Compose dev repo, and the project's own
  modules are consumed as project dependencies.
- Dead config plus stale docs (CLAUDE.md still documents GH Packages publishing
  and `gpr.user`/`gpr.key` setup) mislead contributors.

## Current state

- `settings.gradle.kts:18-35`:

  ```kotlin
  @Suppress("UnstableApiUsage")
  dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
      google()
      mavenCentral()
      maven {
        url = uri("https://maven.pkg.github.com/kioba/anchor")
        credentials {
          username = getProperty("gpr.user") ?: System.getenv("USERNAME")
          password = getProperty("gpr.key") ?: System.getenv("TOKEN")
        }
        authentication {
          create<BasicAuthentication>("basic")
        }
      }
    }
  }
  ```

  Note `import java.lang.System.getProperty` at the top of the file (line 1)
  is used by this block — remove it too if nothing else uses it.
- CLAUDE.md sections referencing GH Packages: "Publishing" (mentions
  `./gradlew publish` to GitHub Packages and `gpr.user`/`gpr.key`) and
  "GitHub Packages Authentication" under Common Issues.
- AGENTS.md may carry the same text (it mirrors CLAUDE.md) — check with
  `grep -n "gpr\.\|GitHub Packages" AGENTS.md CLAUDE.md README.md`.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Resolution check | `./gradlew :anchor:dependencies --configuration desktopCompileClasspath -q` | exit 0, no `maven.pkg.github.com` |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `settings.gradle.kts` (remove the `maven { ... }` block and, if now unused,
  the `getProperty` import)
- `CLAUDE.md`, `AGENTS.md` (remove/replace GH Packages setup text)

**Out of scope**:
- `pluginManagement` repositories (the JetBrains Compose dev repo there is
  needed).
- `.github/workflows/package-publish.yml` (already Maven-Central-only per #232).
- README installation instructions (Maven Central coordinates — untouched).

## Git workflow

- Branch: `chore/remove-ghp-repo`
- Commit style: gitmoji, e.g. `🔥 Remove vestigial GitHub Packages repository config`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Baseline — prove nothing resolves from GH Packages

`./gradlew build --offline` is unreliable here; instead run
`./gradlew :anchor:dependencies --configuration desktopCompileClasspath -q`
and `grep -rn "maven.pkg.github.com" --include="*.kts" --include="*.toml" . | grep -v .claude | grep -v build/` —
the only hit should be `settings.gradle.kts`.

**Verify**: only one hit, in `settings.gradle.kts`.

### Step 2: Remove the repository block

Delete the `maven { ... }` block (and the `BasicAuthentication` import usage
it carried). Delete `import java.lang.System.getProperty` if it is now unused
(confirm with `grep -n getProperty settings.gradle.kts`).

**Verify**: `./gradlew build` → exit 0 (everything still resolves).

### Step 3: Clean the docs

In CLAUDE.md and AGENTS.md remove the GitHub Packages publishing command,
credential setup (`gpr.user`/`gpr.key`, `USERNAME`/`TOKEN`), and the
"GitHub Packages Authentication" troubleshooting entry. Keep the Maven Central
publishing docs. Do NOT touch version strings (CI gates them).

**Verify**: `grep -n "gpr\.\|maven.pkg.github" CLAUDE.md AGENTS.md settings.gradle.kts` → no matches.

## Test plan

No behavioral code; the verification commands are the test. Run
`./gradlew build` once with a clean configuration cache
(`./gradlew --stop && ./gradlew build`) to ensure settings changes are picked up.

## Done criteria

- [ ] `settings.gradle.kts` has no GH Packages repo and no credential reads
- [ ] `grep -rn "maven.pkg.github.com" --include="*.kts" .` (excluding `.claude/`, `build/`) → no matches
- [ ] CLAUDE.md / AGENTS.md no longer document GH Packages auth
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- Step 1 shows any dependency actually resolving from `maven.pkg.github.com`
  (then the repo is load-bearing — report which artifact).
- The build fails after removal with a resolution error (report the artifact;
  do not re-add the repo silently).

## Maintenance notes

- If GH Packages consumption is ever reintroduced, use explicitly-named
  variables (`ORG_GRADLE_PROJECT_gprUser` style), never bare `USERNAME`/`TOKEN`.
- Reviewer: double-check the dependabot daily run still passes after merge
  (it resolves dependencies with this settings file).
