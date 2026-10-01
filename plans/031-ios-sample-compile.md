# Plan 031: Make the iOS sample compile again (typed `Err` in the Swift wrapper)

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0048d11..HEAD -- iosApp features/config/src/commonMain umbrella .github/workflows/ios_check.yml`
> (`0048d11` = `0048d1173e712e4d6e96bed67741faab22ccf467`, the tip of
> `origin/fix/004-init-event-delivery`. These paths are identical on
> origin/master `0bc430c`.)
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition. Two changes are **expected drift**:
> - PR #279 (plan 005) changes `ViewModelProtocol.swift`. If it has landed, apply Step 2's substitution to its version (see Step 2).
> - PR #268 changes `ios_check.yml` (setup-android v4).

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW. The change is sample-only, and no published artifact changes.
- **Depends on**: none. Coordinate with PR #279 (plan 005), which edits the same Swift file (spec Q2). The CI step goes after PR #268, which edits the same workflow.
- **Category**: bug (sample build) + CI
- **Planned at**: `0048d11` (identical for these paths on origin/master `0bc430c`), 2026-09-29
- **Spec**: [`plans/specs/031-ios-sample-compile.md`](specs/031-ios-sample-compile.md)
- **Issue**: none. The break came from #207, not #183 (spec §1.3). Recommend a dedicated issue.
- **Gating**: Steps 1–3 need no decision. Step 4 (the CI guard) **requires a maintainer GO on spec Q1**.

## Why this matters

The repo's only Swift example does not compile. `xcodebuild` exits 65 on `ConfigView.swift:5:66`, because #207 gave `configAnchor()` a typed `ConfigError` while the Swift wrapper still hardcodes `KotlinNothing`. It has been broken since 2026-05-05 without anyone noticing, because no CI job compiles Swift. The fix makes the wrapper generic over the error type, and a prototype of it built green on `0bc430c`. A scheduled `xcodebuild` step would keep the same break from recurring silently.

## Current state

- `iosApp/iosApp/ViewModelProtocol.swift:24-44`:

  ```swift
  final class ViewModel<E, S>: ObservableObject where E: Effect, S: ViewState {
    let anchorInstance: shared.Anchor<E, S, KotlinNothing>
    var anchor: AnchorAction<shared.Anchor<E, S, KotlinNothing>>
    @Published var state: S
    @Published var signal: SwiftSignalProvider

    private var stateCollector: NativeCancellable?
    private var signalCollector: NativeCancellable?

    init(factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S, KotlinNothing>) {
      let localAnchor = RememberAnchorKt.rememberAnchor(
        scope: { scope in factory(scope) as! shared.Anchor<any Effect, any ViewState, AnyObject> },
        customKey: nil
      ) as! shared.Anchor<E, S, KotlinNothing>

      self.anchorInstance = localAnchor
      self.anchor = { action in Task { try await action(localAnchor) } }
      self.state = localAnchor.state as! S
      self.signal = SwiftSignalProvider(signal: UnitSignal())

      let sink = localAnchor as! shared.AnchorSink<E, S, KotlinNothing>
  ```

- `ViewModelProtocol.swift:61-75`: `private struct EnvironmentBinding<E, S>: ViewModifier where E: Effect, S: ViewState` with `let viewModel: ViewModel<E, S>`, and `func environmentAnchor<E: Effect, S: ViewState>(_ viewModel: ViewModel<E, S>)` calling `modifier(EnvironmentBinding<E, S>(viewModel: viewModel))`.
- `iosApp/iosApp/ConfigView.swift:5` is `@StateObject var viewModel = ViewModel(factory: ConfigAnchorKt.configAnchor)`, and `:14` is `@Binding var anchor: AnchorAction<shared.Anchor<ConfigEffect, ConfigState, KotlinNothing>>`.
- `features/config/src/commonMain/kotlin/dev/kioba/anchor/features/config/data/ConfigAnchor.kt:11` is `internal typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState, ConfigError>`.
- `CounterView.swift:19,88` and `HomeView.swift:14` use `KotlinNothing`. They are correct and stay unchanged, because their anchors are pure.
- `.github/workflows/ios_check.yml:39-44` compiles Kotlin only:

  ```yaml
        - name: Compile iOS targets
          run: >
            ./gradlew
            compileKotlinIosArm64
            compileKotlinIosSimulatorArm64
            --stacktrace
  ```

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| Build the sample | `xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator -destination "generic/platform=iOS Simulator" -derivedDataPath build/xcode-dd CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build` | `** BUILD SUCCEEDED **`, exit 0. Before the fix: exit 65. |
| Errors only | `grep -n " error:" <log>` | Before the fix: only `ConfigView.swift:5:66`. After: no output. |
| New Swift warnings | `grep -o "iosApp/iosApp/[A-Za-z]*\.swift:[0-9]*:[0-9]*: warning: .*" <log> \| sort -u` | Only the pre-existing `ViewModelProtocol.swift:40` warning |

- `build/` is gitignored (`.gitignore:102`).
- The build runs `./gradlew :umbrella:embedAndSignAppleFrameworkForXcode` (`project.pbxproj:188`). The first run takes several minutes.
- Afterwards, delete `build/xcode-dd`: it takes about 150 MB.

## Scope

**In scope**:
- `iosApp/iosApp/ViewModelProtocol.swift`: add the `Err` generic parameter.
- `iosApp/iosApp/ConfigView.swift`: line 14.
- `.github/workflows/ios_check.yml`: one new step (Step 4, only with a GO).

**Out of scope**:
- `CounterView.swift` and `HomeView.swift` (unchanged).
- Any Kotlin code, including `configAnchor()`'s type.
- The `ViewModelProtocol.swift:40` `Task` warning (spec Q3).
- `AnchorContainer` (plan 005) and a Swift DX layer (plan 019).

## Git workflow

- Branch: `fix/031-ios-sample-compile`, from `master` (spec Q2).
- Commits:
  - `🐛 Make the iOS sample's ViewModel generic over the anchor's error type`
  - `💚 Build the iOS sample in the scheduled iOS check` (Step 4, if GO)
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Reproduce the failure (test-first)

Run the "Build the sample" command and save its output to a log.

**Verify**:
- The exit code is 65.
- `grep -n " error:"` shows only `iosApp/iosApp/ConfigView.swift:5:66: error: cannot convert value of type '(any RememberAnchorScope) -> Anchor<ConfigEffect, ConfigState, any ConfigError>' to expected argument type '(any RememberAnchorScope) -> Anchor<ConfigEffect, ConfigState, KotlinNothing>'`.
- The Gradle script phase succeeded, so the failing commands are only `SwiftCompile` and `SwiftEmitModule`.

### Step 2: Make the wrapper generic over `Err`

In `ViewModelProtocol.swift`, make these edits. This is exactly the diff the spec's prototype built green.

- Line 24: `final class ViewModel<E, S, Err>: ObservableObject where E: Effect, S: ViewState, Err: AnyObject {`
- Replace every `shared.Anchor<E, S, KotlinNothing>` with `shared.Anchor<E, S, Err>` (lines 25, 26, 33 and 37), and `shared.AnchorSink<E, S, KotlinNothing>` with `shared.AnchorSink<E, S, Err>` (line 44).
- Leave line 35's `as! shared.Anchor<any Effect, any ViewState, AnyObject>` as it is.
- `EnvironmentBinding<E, S, Err>: ViewModifier where E: Effect, S: ViewState, Err: AnyObject`, with `let viewModel: ViewModel<E, S, Err>`.
- `func environmentAnchor<E: Effect, S: ViewState, Err: AnyObject>(_ viewModel: ViewModel<E, S, Err>)` returning `modifier(EnvironmentBinding<E, S, Err>(viewModel: viewModel))`.

In `ConfigView.swift:14`, write `@Binding var anchor: AnchorAction<shared.Anchor<ConfigEffect, ConfigState, any ConfigError>>`.

If PR #279 has landed, apply the same rule to its version: every `KotlinNothing` in `ViewModelProtocol.swift` becomes `Err`, including `container.anchor as! shared.Anchor<E, S, Err>`, and `Err: AnyObject` goes on the same three declarations. `AnchorContainer<E, S>` stays two-parameter.

**Verify**: `grep -n "KotlinNothing" iosApp/iosApp/ViewModelProtocol.swift iosApp/iosApp/ConfigView.swift` prints nothing.

### Step 3: Rebuild

Run "Build the sample" again.

**Verify**:
- The build prints `** BUILD SUCCEEDED **` and exits 0.
- There is no ` error:` line.
- The only Swift warning is the pre-existing `ViewModelProtocol.swift:40`.

Optional smoke test, recorded in the PR if you do it:
- Boot a simulator (`xcrun simctl boot "<device>"`), install and launch the app, and open the Config tab.
- Type some text. About 1 s later the label below the field shows it (`ConfigEffects.kt:10-16`).
- Clear the field. The app must not crash. Clearing raises `ConfigError.EmptyInput` (`ConfigEffects.kt:11`), which `onDomainError` stores in `errorMessage`. `ConfigUi` renders only `state.text` (`ConfigView.swift:30-32`), so there is nothing visible to check.

### STOP: maintainer GO required for Step 4

Report Steps 1–3, and ask for spec Q1: (g1) scheduled `ios_check`, (g2) every PR, or (g3) none. The recommendation is (g1). Also confirm whether PR #268 has merged. **Without a GO, finish at Step 3.**

### Step 4 (GO on Q1): Build the sample in CI

For (g1), rebase onto or wait for #268, then append this step to `ios_check.yml` after "Compile iOS targets":

```yaml
      - name: Build the iOS sample app
        run: >
          xcodebuild
          -project iosApp/iosApp.xcodeproj
          -scheme iosApp
          -configuration Debug
          -sdk iphonesimulator
          -destination "generic/platform=iOS Simulator"
          -derivedDataPath build/xcode-dd
          CODE_SIGNING_ALLOWED=NO
          ARCHS=arm64
          build
```

For (g2), add the same step as a new `macos-latest` job in `pr_check.yml` instead, with the checkout, JDK, Android SDK, Gradle and Xcode setup steps copied from `ios_check.yml:19-37`.

**Verify**: the YAML parses (`python3 -c "import yaml,sys; yaml.safe_load(open(sys.argv[1]))" .github/workflows/ios_check.yml`). If the operator allows it, trigger a `workflow_dispatch` run and see it go green. Otherwise note in the PR that it is untested in CI.

## Test plan

- **Test-first**: Step 1's failing `xcodebuild` is the regression test. It fails with exit 65 on `ConfigView.swift:5:66` before the change, as reproduced for the spec on `0bc430c`. It succeeds after, as the spec's prototype did on `0bc430c`.
- The sample has no Swift test target. The optional simulator smoke test in Step 3 checks that the typed anchor still dispatches, and that raising `ConfigError` does not crash.
- Regression: `CounterView` and `HomeView` compile unchanged. That is covered by the same `xcodebuild`, since `Err` is inferred as `KotlinNothing` from their factories.
- The Kotlin side is untouched, so no Gradle test tasks are needed.

## Done criteria

- [ ] The failing `xcodebuild` (exit 65, `ConfigView.swift:5:66`) is recorded in the PR
- [ ] `xcodebuild` prints `** BUILD SUCCEEDED **`, with no new Swift warnings
- [ ] No `KotlinNothing` remains in `ViewModelProtocol.swift`, and `ConfigUi` uses `any ConfigError`
- [ ] Step 4 is done with the chosen option, or explicitly skipped for lack of a GO
- [ ] `build/xcode-dd` is deleted locally
- [ ] The `plans/README.md` status row is updated

## STOP conditions

Stop and report back if:

- Step 1 fails in the Gradle script phase, or shows Swift errors other than `ConfigView.swift:5:66`. The environment or the Kotlin side has drifted.
- Step 3 reports that `any ConfigError` does not satisfy `Err: AnyObject`. The spec's prototype on Xcode 27.0 accepted it; a different Xcode may not. Report the Xcode version and the error, and do not fall back to option (b) without a GO.
- PR #279 has landed in a shape where `ViewModelProtocol.swift` no longer has one `KotlinNothing`-typed `ViewModel`. The mechanical rule no longer applies.
- Step 4 is reached without a GO on Q1.

## Maintenance notes

- Any future typed-`Err` feature needs no Swift wrapper change. The view only declares its `AnchorAction<shared.Anchor<…, any MyError>>`.
- If plan 019 lands a Swift DX layer, it replaces `ViewModel`. Keep the CI build step.
- Reviewer: check that the change adds no `as!` casts to views, that `KotlinNothing` remains only in `CounterView` and `HomeView`, and that `ios_check.yml` still has the Kotlin compile step.
