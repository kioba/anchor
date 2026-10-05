# Plan 018: Spike — opt-in ViewState save/restore across process death

> **Executor instructions**: This is a DESIGN/SPIKE plan. The deliverable is a
> design note at `plans/design/state-restoration.md` plus a prototype in a
> disposable worktree — NOT a production change. Do not modify any file
> outside `plans/`. Follow the steps, answer the open questions with
> evidence, and STOP at the decision gate. When done, update the status row
> in `plans/README.md`.
>
> **Drift check (run first)**: `git fetch origin --quiet; git diff --stat origin/master -- anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ anchor/src/commonMain/kotlin/dev/kioba/anchor/RememberAnchorScope.kt gradle/libs.versions.toml`
> Work against **origin/master** (local 492f7bc is 7 commits behind, tip ec1017c).

## Status

- **Priority**: P3
- **Effort**: M (spike)
- **Risk**: LOW (read-only spike; the eventual feature is MED)
- **Depends on**: none
- **Category**: direction
- **Planned at**: commit `492f7bc` (origin/master `ec1017c`), 2026-06-11

## Why this matters

Anchor retains state across configuration changes (ViewModel) and promises
exactly that — `docs/compose.md` "Lifecycle" §: "The ViewModel survives
configuration changes — state is retained automatically." It has **no story
for process death** (Android low-memory kill) or iOS suspension+kill: state
always re-seeds from the `initialState()` factory
(`AnchorRuntime.kt:54` — `MutableStateFlow(initialState())`). Forms, carts,
search results, navigation-adjacent state all reset.

Two more pieces of repo evidence make this the grounded next capability:

- `ContainerViewModel` (`viewmodel/ContainerViewModel.kt:17-20`) takes only
  the runtime — no `SavedStateHandle`, no extras — yet ViewModel creation is
  already centralized in `anchorContainerViewModelFactory`, the exact seam a
  restoration hook needs.
- **kotlinx-serialization is declared but used by zero lines of code**:
  `gradle/libs.versions.toml` declares `kotlinxSerializationCoreJvm`/`kotlinxSerializationJson`,
  feature builds reference them, and
  `grep -rn "kotlinx.serialization\|@Serializable" --include="*.kt" anchor anchor-compose anchor-test features androidApp umbrella` (excluding build/)
  returns nothing. Either this spike gives the dependency its job, or the
  spike's outcome includes "drop it".

The library's stated migration targets (Orbit/MVIKotlin — see #140's scope in
issue #237) both have state-keeping answers; Anchor competing without one is
a real adoption gap.

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/RememberAnchorScope.kt:37-44` —
  the factory surface a saver would extend:

  ```kotlin
  public fun <R, S, Err> create(
    effectScope: () -> R,
    initialState: () -> S,
    init: (suspend Anchor<R, S, Err>.() -> Unit)? = null,
    onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)? = null,
    defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)? = null,
    subscriptions: (suspend SubscriptionsScope<R, S, Err>.() -> Unit)? = null,
  ): Anchor<R, S, Err> where R : Effect, S : ViewState, Err : Any
  ```

- `anchor-compose/.../RememberAnchor.kt:157-161` — ViewModel creation goes
  through `viewModel(key, factory = anchorContainerViewModelFactory { scope() })`;
  androidx `viewModel()` can hand a `CreationExtras`-provided
  `SavedStateHandle` to the factory.
- State writes are centralized: `AnchorRuntime.reduce` →
  `_viewState.update(reducer)` (`AnchorRuntime.kt:120-123`) — a save-on-write
  or save-on-stop hook has exactly one choke point.
- Relevant platform facts to verify in step 1 (believed true at planning):
  `androidx.savedstate` ≥ 1.3 and `lifecycle-viewmodel-savedstate` are
  Kotlin Multiplatform, with kotlinx-serialization-based `SavedState`
  encoding; the repo already uses lifecycle 2.10.0 multiplatform artifacts
  (`libs.versions.toml`: `viewmodel = "2.10.0"`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Confirm serialization unused | the grep above | empty |
| Prototype build (worktree) | `./gradlew :anchor:build` | exit 0 |
| Prototype behavior | `./gradlew :anchor:desktopTest --tests "*Restoration*"` | exit 0 |

## Scope

**In scope** (writable): `plans/design/state-restoration.md`,
`plans/README.md`, a disposable worktree for the prototype.

**Out of scope**: any production change; shipping the feature (a follow-up
plan after the note is accepted); the unused-dependency removal itself
(recommendation only).

## Steps

### Step 1: Verify the platform surface

Confirm (with version numbers and doc/source citations in the note):
(a) `SavedStateHandle` availability in commonMain via current lifecycle 2.10
artifacts; (b) the KMP `SavedState` + kotlinx-serialization `encodeToSavedState`/
`decodeFromSavedState` API status; (c) what iOS/Desktop targets get (real
persistence vs in-memory only) — be precise about where the multiplatform
story genuinely ends.

**Verify**: §1 of the note states, per platform, what survives: config change /
process death / full relaunch.

### Step 2: Design the opt-in API

Sketch (code, not prose) the minimal additive surface. Anchor's shape suggests:

```kotlin
create(
  initialState = ::MyState,
  effectScope = { ... },
  saver = stateSaver(MyState.serializer()),   // optional; default = no restoration
)
```

Cover in the design: where the handle comes from
(`anchorContainerViewModelFactory` via `CreationExtras`), when saves happen
(every `reduce` vs `SavedStateHandle.setSavedStateProvider` lazy snapshot at
save time — prefer the lazy provider; justify), key derivation (reuse
`RememberAnchor`'s `customKey ?: S::class.qualifiedName` — note the collision
rules), what is explicitly NOT restored (signals, events, in-flight jobs),
size limits (TransactionTooLargeException guidance), and how `PureAnchor`
typealiases stay source-compatible.

**Verify**: §2 contains the full API sketch + a sequence diagram (text is
fine) of restore-on-create and save-on-stop.

### Step 3: Prototype in a disposable worktree

Smallest end-to-end proof: `ContainerViewModel` variant accepting
`SavedStateHandle`, a `TestState` with a serializer, a desktop test that
simulates kill/recreate (new ViewModel from the same handle state). Include
the diff as the note's appendix; discard the worktree.

**Verify**: prototype test passes in the worktree; appendix included.

### Step 4: Recommend and stop

§4: ship / don't-ship recommendation; if ship — phasing (Android-first vs
KMP-first), and the explicit follow-up list (implementation plan, docs
update correcting the `compose.md` retention wording, serialization dep
decision). If don't-ship — recommend removing the unused kotlinx-serialization
declarations. **STOP.**

## Test plan

The step-3 prototype test (worktree-only). Nothing lands.

## Done criteria

- [ ] `plans/design/state-restoration.md` with §1 platform truth, §2 API
      design, §3 prototype appendix, §4 recommendation + open questions
- [ ] No file outside `plans/` modified in the user's tree (`git status`)
- [ ] `plans/README.md` row updated

## STOP conditions

- End of Step 4 is a mandatory stop.
- If step 1 shows KMP `SavedState` cannot reach commonMain with current
  dependency versions, pivot the note to an Android-only `expect/actual`
  design and say so prominently.
- If origin/master gained any restoration mechanism since planning
  (re-run the SavedStateHandle grep against origin/master first), this plan
  becomes a review of it.

## Maintenance notes

- Interacts with plan 016 (module layout) only trivially; with plan 005
  (iOS container) more substantially — an iOS restoration story would hang
  off `AnchorContainer`.
- If shipped, `docs/compose.md`'s lifecycle section and #140's docs overhaul
  must state the new guarantee — and the llms-full.txt regeneration gate
  applies to those edits.
