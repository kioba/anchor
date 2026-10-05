# Spec: The iOS sample app does not compile (`ConfigView.swift:5`)

- **Issue**: none yet. It was reported to the triage as "broken since PR #183 added a `ConfigError` type parameter". That attribution is **wrong** (§1.3): #183 added the `Err` parameter and updated the Swift correctly, and the break came from #207. Recommend the maintainer open one issue.
- **Verdict**: CONFIRMED. On origin/master `0bc430c`, with Xcode 27.0 (27A266a), `xcodebuild` exits 65 with exactly one Swift error. The iosApp, `features/` and `umbrella` trees are identical on the runtime stack tip `0048d11`, so the break is independent of the in-flight PRs. It has been broken since #207 (`b8f596b`, merged 2026-05-05).
- **Severity**: P2.
  - It is sample-only: the published libraries are unaffected.
  - It is the repo's only Swift consumer example, and anyone who opens `iosApp` in Xcode hits a compile error.
  - It went unnoticed for five months because no CI job compiles Swift. That is a test-infra gap that hides bugs.
- **Verified at**: origin/master `0bc430c`, 2026-09-29, in a disposable worktree that has since been removed.
- **Plan**: [`plans/031-ios-sample-compile.md`](../031-ios-sample-compile.md). The Swift fix needs no decision. Adding a CI guard is gated on Q1.
- **Related**:
  - Plan 005 / PR #279 rewrites the same `ViewModelProtocol.swift` lines.
  - PR #268 edits `ios_check.yml`.
  - Plan 019 covers the Swift DX layer.
  - `docs/errors.md` is built around `ConfigError`.

---

## 1. Problem statement and evidence

### 1.1 The failure

```
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp -configuration Debug -sdk iphonesimulator \
  -destination "generic/platform=iOS Simulator" -derivedDataPath <scratch> CODE_SIGNING_ALLOWED=NO ARCHS=arm64 build
```

The command exits with 65. The log contains one error:

```
iosApp/iosApp/ConfigView.swift:5:66: error: cannot convert value of type '(any RememberAnchorScope) -> Anchor<ConfigEffect, ConfigState, any ConfigError>' to expected argument type '(any RememberAnchorScope) -> Anchor<ConfigEffect, ConfigState, KotlinNothing>'
** BUILD FAILED **
The following build commands failed:
	SwiftCompile normal arm64 (in target 'iosApp' from project 'iosApp')
	SwiftEmitModule normal arm64 Emitting\ module\ for\ iosApp (in target 'iosApp' from project 'iosApp')
```

The Gradle build phase (`./gradlew :umbrella:embedAndSignAppleFrameworkForXcode`, `project.pbxproj:188`) succeeded, so the Kotlin side is fine. The plan-005 executor's own `xcodebuild` run on its branch hit the same error, independently.

### 1.2 Cause

- The Swift wrapper hardcodes a `KotlinNothing` error type (`iosApp/iosApp/ViewModelProtocol.swift:24-44`):

  ```swift
  final class ViewModel<E, S>: ObservableObject where E: Effect, S: ViewState {
    let anchorInstance: shared.Anchor<E, S, KotlinNothing>
    var anchor: AnchorAction<shared.Anchor<E, S, KotlinNothing>>
    ...
    init(factory: @escaping (any RememberAnchorScope) -> shared.Anchor<E, S, KotlinNothing>) {
  ```

  The same file uses `KotlinNothing` again at `:37` and `:44`, and `EnvironmentBinding`/`environmentAnchor` (`:61-75`) are generic over `<E, S>` only.
- `configAnchor()` returns a typed error (`features/config/src/commonMain/kotlin/dev/kioba/anchor/features/config/data/ConfigAnchor.kt:11-13`):

  ```kotlin
  internal typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState, ConfigError>

  public fun RememberAnchorScope.configAnchor(): ConfigAnchor =
  ```

  ObjC export turns it into `Anchor<ConfigEffect, ConfigState, any ConfigError>`.
- `ConfigView.swift:5` passes it to `ViewModel(factory:)`, and `ConfigView.swift:14` declares `AnchorAction<shared.Anchor<ConfigEffect, ConfigState, KotlinNothing>>`.
- `CounterView` and `HomeView` still compile: their anchors are `PureAnchor`s, so `Err` is `Nothing` / `KotlinNothing`.

### 1.3 History (which PR broke it)

- **#183** (`5accfd1`, merged 2026-04-15) added the `Err` type parameter. It changed `ConfigView.swift:14` from `Anchor<ConfigEffect, ConfigState>` to `Anchor<ConfigEffect, ConfigState, KotlinNothing>`. That matched the Kotlin at the time: at `5accfd1`, `features/config/.../counter/data/ConfigAnchor.kt:9` is `internal typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState, Nothing>`.
- **#207** (`b8f596b`, "📝 Add error handling documentation", merged 2026-05-05) changed that typealias to `ConfigError`, added `ConfigError.kt`, and moved the file to `config/data/`. At `b8f596b^` the typealias still says `Nothing`. **#207's diff touches no file under `iosApp/`.**

### 1.4 Why it went unnoticed

- `.github/workflows/ios_check.yml:39-44` compiles only `compileKotlinIosArm64 compileKotlinIosSimulatorArm64`, weekly and on demand.
- `pr_check.yml` runs on `ubuntu-latest`.
- No workflow runs `xcodebuild`, so Swift is never compiled in CI.

---

## 2. Goals and non-goals

**Goals**
- The sample builds with `xcodebuild` on master.
- The Config screen keeps its typed `ConfigError`, which the error-handling docs are built around.
- A Swift compile break surfaces in CI within days (Q1).

**Non-goals**
- A Swift DX layer or a SKIE-like bridge (plan 019).
- The `AnchorContainer` lifecycle (plan 005).
- The sample's runtime behaviour.
- The pre-existing warning at `ViewModelProtocol.swift:40` ("unstructured throwing task created by 'init(name:priority:operation:)' is not used").

---

## 3. Design

| Option | Sketch | Verdict |
|---|---|---|
| **(a) Generic `Err` in the Swift wrapper** | `ViewModel<E, S, Err> where …, Err: AnyObject`. Every `KotlinNothing` in `ViewModelProtocol.swift` becomes `Err`, and `EnvironmentBinding`/`environmentAnchor` gain `Err: AnyObject`. `ConfigView.swift:14` becomes `Anchor<ConfigEffect, ConfigState, any ConfigError>`. `CounterView` and `HomeView` are unchanged, because `Err` is inferred as `KotlinNothing` from their factories. | **Recommended.** The prototype on `0bc430c` changed 2 files (+12/−12), and a rebuild printed `** BUILD SUCCEEDED **` (exit 0). This also proves that `any ConfigError` satisfies `Err: AnyObject`. |
| (b) Force-cast in `ConfigView` | `ViewModel(factory: { configAnchor($0) as! Anchor<ConfigEffect, ConfigState, KotlinNothing> })` | Rejected. It compiles only because ObjC generics are erased at runtime, and it misstates the error type in the very example that demonstrates typed errors. |
| (c) Revert `configAnchor()` to `Nothing` | | Rejected: it removes the `ConfigError` example that `docs/errors.md` is built around (#207) |
| (d) Erase `Err` to `AnyObject` in the wrapper | `shared.Anchor<E, S, AnyObject>` everywhere | Rejected: the views lose the type, and each view needs casts |

**CI guard (Q1)**

| Option | Sketch | Verdict |
|---|---|---|
| **(g1) `xcodebuild` step in `ios_check.yml`** | Scheduled Wednesday and Friday, plus `workflow_dispatch`, on `macos-latest`, after the Kotlin compile step | **Recommended.** It costs little and would have caught #207 within 5 days, the longest gap in the Wednesday/Friday schedule. |
| (g2) New job in `pr_check.yml` | `macos-latest` on every PR | This catches breaks before merge, but costs macOS minutes on every PR, including Dependabot's. |
| (g3) None | | Rejected: the same break would recur silently |

### 3.1 Interactions

- **PR #279 (plan 005)** rewrites `ViewModelProtocol.swift`. It adds `AnchorContainer<E, S>`, uses `createAnchor(...)`, and keeps `KotlinNothing` in `anchorInstance`, `anchor`, `init(factory:)` and `container.anchor as! …`. The substitution is mechanical either way: `KotlinNothing` becomes `Err`, and `Err: AnyObject` is added to the three generic declarations. `AnchorContainer<E, S>` stays two-parameter. Whichever PR lands second applies it (Q2).
- **PR #268** edits `ios_check.yml` (setup-android v3 → v4, which is also needed because v3 has broken Android-SDK jobs since about 2026-09-16). Land the (g1) step after #268, or rebase onto it.
- **Plan 019** will replace this hand-written wrapper. (a) is the minimal honest fix until then.

## 4. Behaviour and API changes

- None to the published libraries. The change is in the sample (`iosApp/`) and, under Q1, CI.
- It is non-breaking.

## 5. Acceptance criteria

1. Before the change, the §1.1 command exits 65 with the `ConfigView.swift:5:66` error. This has been reproduced.
2. After the change, it prints `** BUILD SUCCEEDED **` and exits 0, with no new Swift warnings. The `:40` warning is pre-existing.
3. `ConfigUi` declares `any ConfigError`, and `KotlinNothing` does not appear in `ViewModelProtocol.swift`.
4. With a Q1 GO: `ios_check.yml` runs the same `xcodebuild` command, and a `workflow_dispatch` run is green.

## 6. Open questions for the maintainer

- **Q1 (CI guard).** Choose (g1) scheduled `ios_check`, (g2) every PR, or (g3) none. **Recommended: (g1).**
- **Q2 (order versus #279).** **Recommended: land 031 off master first.** It is two Swift files, and #279 then rebases with the mechanical substitution in §3.1. If #279 merges first, apply 031 on top of its version.
- **Q3 (the pre-existing `:40` warning).** **Recommended: leave it.** It is unrelated, and plan 005 rewrites that initializer.

## 7. Relationship to existing plans

| Plan | Relationship |
|---|---|
| 005 (#279) | Same file, and the conflicts are mechanical (§3.1). |
| #268 | Same workflow file for (g1). |
| 019 | A future Swift layer supersedes this wrapper. |
| 028 | None. The docs do not show the Swift wrapper. |
