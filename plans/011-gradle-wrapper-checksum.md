# Plan 011: Pin the Gradle distribution checksum in the wrapper

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- gradle/wrapper/gradle-wrapper.properties`
> If the Gradle version changed since planning, fetch the checksum for the NEW
> version instead — the step-1 URL pattern still applies.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: none
- **Category**: security
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

This repo publishes artifacts to Maven Central; its build toolchain integrity
is part of its supply chain. The wrapper config validates the distribution
*URL* (`validateDistributionUrl=true`) but not the distribution *content* — a
compromised download/mirror could deliver tampered build tooling. Gradle
supports a one-line content pin: `distributionSha256Sum`.

## Current state

- `gradle/wrapper/gradle-wrapper.properties` (entire file):

  ```properties
  distributionBase=GRADLE_USER_HOME
  distributionPath=wrapper/dists
  distributionUrl=https\://services.gradle.org/distributions/gradle-9.5.0-bin.zip
  networkTimeout=10000
  retries=0
  retryBackOffMs=500
  validateDistributionUrl=true
  zipStoreBase=GRADLE_USER_HOME
  zipStorePath=wrapper/dists
  ```

- Dependabot bumps `gradle-wrapper` (see commit `7ecceae` "⬆️ Bump
  gradle-wrapper from 9.4.1 to 9.5.0"); the official wrapper-update path
  (`./gradlew wrapper --gradle-version X --gradle-distribution-sha256-sum <sha>`)
  maintains the checksum, and Dependabot updates `distributionSha256Sum` when
  present.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Fetch official checksum | `curl -sfL https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256` | 64-hex-char string |
| Wrapper run | `./gradlew help` | exit 0 |

## Scope

**In scope**:
- `gradle/wrapper/gradle-wrapper.properties`

**Out of scope**:
- `gradle-wrapper.jar`, `gradlew`, `gradlew.bat` (no regeneration needed for a
  properties-only change).
- Dependency verification metadata (`gradle/verification-metadata.xml`) —
  considered and rejected by the advisor (maintenance cost too high for this
  repo's size).

## Git workflow

- Branch: `chore/wrapper-checksum`
- Commit style: gitmoji, e.g. `🔒️ Pin Gradle distribution checksum in wrapper`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Fetch the official checksum

`curl -sfL https://services.gradle.org/distributions/gradle-9.5.0-bin.zip.sha256`
(match the exact version in `distributionUrl`). Cross-check the value against
https://gradle.org/release-checksums/ if reachable.

**Verify**: output is a single 64-character hex string.

### Step 2: Add the pin

Append to `gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionSha256Sum=<the fetched hex string>
```

### Step 3: Force a validated download

The already-cached distribution skips validation, so verify against a fresh
download: run with an isolated Gradle home:

`GRADLE_USER_HOME=$(mktemp -d) ./gradlew help`

This re-downloads the distribution and fails loudly on checksum mismatch.
(It will take a few minutes; that is expected. If sandbox/network constraints
block it, fall back to `./gradlew help` + manually diffing your pinned value
against the step-1 URL output a second time.)

**Verify**: exit 0.

## Test plan

Step 3 is the test (checksum actually validated against a fresh download).

## Done criteria

- [ ] `distributionSha256Sum` present and matches the official value for the
      pinned version
- [ ] `./gradlew help` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- The fetched checksum differs between the `.sha256` endpoint and
  release-checksums page (do not pick one — report).
- Step 3 fails checksum validation (possible tampering or version mismatch —
  report immediately, do not weaken the pin).

## Maintenance notes

- Future wrapper bumps MUST update the checksum in the same change; Dependabot
  does this automatically once the property exists. A bump PR that removes the
  property should be rejected in review.
