# Plan 019: Design — Swift-side DX parity layer (or SKIE) for iOS consumers

> **Executor instructions**: This is a DESIGN/SPIKE plan. The deliverable is a
> design note at `plans/design/ios-dx-parity.md` with a prototype Swift sketch
> in its appendix — NOT a production change. Do not modify any file outside
> `plans/`. Follow the steps and STOP at the decision gate. When done, update
> the status row in `plans/README.md`.
>
> **Drift check (run first)**: `git fetch origin --quiet; git diff --stat origin/master -- anchor/src/iosMain/ iosApp/`
> Work against **origin/master** (local 492f7bc is 7 commits behind). If plan
> 005 (AnchorContainer) has landed, design on top of it; if not, design
> against its API as specified in `plans/005-ios-anchor-lifecycle.md` and say
> which assumption you took.

## Status

- **Priority**: P3
- **Effort**: M (design + prototype sketch)
- **Risk**: LOW (spike)
- **Depends on**: plans/005-ios-anchor-lifecycle.md (conceptually; see drift check)
- **Category**: direction
- **Planned at**: commit `492f7bc` (origin/master `ec1017c`), 2026-06-11

## Why this matters

On Android/Desktop, Anchor gives you `RememberAnchor` + `collectState` +
`HandleSignal` + `anchor()` — retention, granular observation, typed signal
handling, and stable callbacks, all declarative. On iOS, every consumer
hand-rolls all of it. The repo's own sample
(`iosApp/iosApp/ViewModelProtocol.swift`) is the evidence — ~80 lines that:

- force-cast **six times** (`as!`) to bridge Kotlin generics
  (`factory(scope) as! shared.Anchor<any Effect, any ViewState, AnyObject>`,
  `localAnchor.state as! S`, `value as! S`, ...);
- conflate signals: `@Published var signal: SwiftSignalProvider` holds only
  the latest signal, and an `Equatable` conformance via `===` was needed to
  make SwiftUI see consecutive distinct signals — the same loss class plan
  002 fixes on the Compose side, reimplemented buggy in user space;
- manage collector lifecycles manually (`stateCollector`/`signalCollector`
  + `deinit` cancel) and — as of this writing — never dispose the underlying
  runtime (no `clear()`; that's plan 005's bug, but the parity layer is where
  the fix becomes ergonomic);
- offer no typed per-signal handling (the sample's views switch on the
  signal type by hand — `CounterView.swift` `onChange` + switch).

Every iOS consumer will write this file, slightly differently, with the same
bugs. The library should own it once — either as a small Swift package layered
on the Kotlin bridges, or by adopting SKIE and deleting the bridges.

## Current state

- Kotlin bridge surface (`anchor/src/iosMain/kotlin/dev/kioba/anchor/`):
  `NativeFlows.kt` — `NativeStateFlow<T>`/`NativeSharedFlow<T>` with
  callback-`collect` returning `NativeCancellable`; `RememberAnchor.kt` —
  `rememberAnchor` (leaky; plan 005 deprecates it for
  `createAnchor(): AnchorContainer` with `clear()`), `nativeViewState()`,
  `nativeSignals()`.
- Swift consumer pattern — `iosApp/iosApp/ViewModelProtocol.swift:24-59`
  (excerpt, current shape):

  ```swift
  final class ViewModel<E, S>: ObservableObject where E: Effect, S: ViewState {
    let anchorInstance: shared.Anchor<E, S, KotlinNothing>
    var anchor: AnchorAction<shared.Anchor<E, S, KotlinNothing>>
    @Published var state: S
    @Published var signal: SwiftSignalProvider
    ...
    self.stateCollector = sink.nativeViewState().collect { [weak self] value in
      self?.state = value as! S
    }
    self.signalCollector = sink.nativeSignals().collect { [weak self] value in
      self?.signal = SwiftSignalProvider(signal: value.provide())
    }
  ```

- The umbrella module (`umbrella/build.gradle.kts`) exports the framework the
  Swift app consumes (`import shared`).
- No Swift Package/CocoaPod exists for helper code; no SKIE anywhere
  (`grep -rni skie --include="*.kts" --include="*.toml" .` → empty, excluding `.claude/`).

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| iOS framework compiles | `./gradlew :umbrella:compileKotlinIosSimulatorArm64` | exit 0 |
| Sample inventory | `ls iosApp/iosApp/` and read each *.swift | worklist for §1 |

## Scope

**In scope** (writable): `plans/design/ios-dx-parity.md`, `plans/README.md`,
disposable worktree for any compile experiments.

**Out of scope**: production Kotlin/Swift changes; SKIE adoption itself;
publishing infrastructure for a Swift package (note the options, don't build).

## Steps

### Step 1: Inventory the full friction surface

Read every file in `iosApp/iosApp/` and list, per file, what the consumer
hand-writes that Compose consumers get free (state observation, signal
routing, action dispatch, lifecycle/disposal, previews). Count the force-casts
and identify which are forced by Kotlin/ObjC generics erasure vs. fixable by
better bridge typing. This is §1 of the note.

### Step 2: Design Option A — first-party Swift helper layer

Sketch (compilable-looking Swift in the appendix) a layer that delivers:

- `AnchorViewModel<E, S>` — `ObservableObject` (and an `@Observable` variant
  note for iOS 17+) owning an `AnchorContainer`, exposing `state`, calling
  `clear()` in `deinit`. Zero force-casts in *consumer* code; quarantine
  unavoidable casts inside the layer with comments.
- Typed signal handling that does NOT conflate: e.g.
  `func handleSignal<T: Signal>(_ type: T.Type, _ handler: @escaping (T) -> Void)`
  backed by a queueing collector, mirroring plan 002's semantics; document
  parity explicitly.
- Action dispatch ergonomics (`vm.dispatch { $0.increment() }` or KeyPath-based).
- Distribution analysis: source file users copy vs SwiftPM package in-repo vs
  publishing the Kotlin framework + Swift wrapper together; CI implications
  (`ios_check.yml` exists — note what it would need to compile Swift).

### Step 3: Design Option B — SKIE

Assess SKIE (Touchlab) against the same checklist: Flows become
`AsyncSequence` (does `for await` replace `NativeFlows` entirely?), sealed
class/generics improvements vs the six casts, build-time and framework-size
cost, license/maintenance posture, and what happens to
`NativeStateFlow`/`NativeSharedFlow`/`SignalProvider` (deprecate? keep for
non-SKIE users?). Where you cannot verify a SKIE behavior from local
evidence, mark it explicitly as "needs a build experiment" rather than
asserting.

### Step 4: Compare, recommend, stop

§4: side-by-side table (consumer LoC for the counter screen, type safety,
maintenance surface, build cost, lock-in), a recommendation, the migration
sketch for the sample app, and open questions for the maintainer (minimum:
"is a Swift package a deliverable this project wants to own?"). **STOP.**

## Test plan

None required beyond compile experiments in a disposable worktree; if you
validate the Swift sketch, note how (e.g. pasted into the iosApp target in a
worktree + xcodebuild) — optional, macOS tooling permitting.

## Done criteria

- [ ] `plans/design/ios-dx-parity.md` with §1 friction inventory (incl. cast
      census), §2 helper-layer design + Swift appendix, §3 SKIE assessment,
      §4 comparison + recommendation + open questions
- [ ] No file outside `plans/` modified (`git status`)
- [ ] `plans/README.md` row updated

## STOP conditions

- End of Step 4 is mandatory.
- If origin/master's iosMain diverged from both the excerpt above AND plan
  005's spec (e.g. a different lifecycle API landed), re-inventory before
  designing; on fundamental mismatch, report instead of designing against air.

## Maintenance notes

- Whichever option wins, the signal-delivery semantics chosen in plan 017
  must be honored by the Swift layer — cross-reference the two notes.
- The sample app (`iosApp/`) becomes the reference implementation; budget its
  rewrite into the follow-up implementation plan, not this spike.
