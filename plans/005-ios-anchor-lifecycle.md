# Plan 005: Give iOS anchors a real lifecycle — retention and disposal instead of a per-call leak

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor/src/iosMain/ iosApp/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P1
- **Effort**: M
- **Risk**: MED
- **Depends on**: none
- **Category**: bug
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

The iOS entry point `rememberAnchor` neither remembers nor cleans up:

1. It creates a **fresh anonymous `ViewModelStoreOwner` on every call**, so no
   retention happens at all — the doc comment ("uses a ViewModelStore to retain
   the Anchor instance") is false.
2. The store is **never `clear()`ed**, so the `ContainerViewModel` created
   inside is never destroyed → its `viewModelScope` is never cancelled → the
   subscription collectors launched in its `init` (see
   `ContainerViewModel.kt:44-53`) run **forever**. Every call from Swift leaks
   an anchor runtime with live coroutines.

Swift consumers need an explicit handle with create/clear semantics they can
tie to their view lifecycle (`deinit` / `.onDisappear`).

## Current state

- `anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt:23-42`:

  ```kotlin
  @Suppress("UNCHECKED_CAST")
  public fun <S, E> rememberAnchor(
    scope: (RememberAnchorScope) -> Anchor<E, S, *>,
    customKey: String? = null,
  ): Anchor<E, S, *>
    where
    E : Effect,
    S : ViewState {
    val storeOwner = object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
    val factory = containerViewModelFactory { scope(AnchorRuntimeScope) as AnchorRuntime<E, S, *> }
    val provider = ViewModelProvider.create(storeOwner, factory)
    val anchorScope = when {
      customKey != null -> provider[customKey, ContainerViewModel::class]
      else -> provider[ContainerViewModel::class]
    } as ContainerViewModel<E, S, *>

    return anchorScope.anchor
  }
  ```

  Also in this file: `nativeViewState()` / `nativeSignals()` extensions
  returning the wrappers from `NativeFlows.kt`.
- `anchor/src/iosMain/kotlin/dev/kioba/anchor/NativeFlows.kt` —
  `NativeStateFlow`/`NativeSharedFlow` each create a
  `CoroutineScope(Dispatchers.Main + SupervisorJob())` per `collect` call and
  return a `NativeCancellable`; these are fine and stay as-is.
- The Swift sample consumes it in `iosApp/iosApp/ViewModelProtocol.swift`
  (collects `nativeViewState()` / `nativeSignals()`).
- `ViewModelStore.clear()` is the lifecycle hook that triggers
  `ViewModel.onCleared()` → cancels `viewModelScope` → cancels the
  subscription job. Nothing calls it today.
- Repo conventions: explicit API mode; KDoc required on public APIs.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| iOS compile | `./gradlew :anchor:compileKotlinIosSimulatorArm64` | exit 0 |
| iOS unit tests | `./gradlew :anchor:iosSimulatorArm64Test` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

(Host is macOS — iOS simulator tasks are available.)

## Scope

**In scope**:
- `anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt`
- `anchor/src/iosMain/kotlin/dev/kioba/anchor/AnchorContainer.kt` (create)
- `anchor/src/iosTest/kotlin/dev/kioba/anchor/AnchorContainerTest.kt` (create;
  create the `iosTest` source directory if absent)
- `iosApp/iosApp/ViewModelProtocol.swift` (best-effort; see STOP conditions)

**Out of scope**:
- `NativeFlows.kt` — collection wrappers are correct.
- `ContainerViewModel` / common `viewmodel/` code.
- The Compose (`anchor-compose`) path — Android/Desktop retention via
  `viewModel()` is correct already.

## Git workflow

- Branch: `fix/ios-anchor-lifecycle`
- Commit style: gitmoji, e.g. `🐛 Add AnchorContainer with explicit disposal for iOS`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Introduce `AnchorContainer`

New file `anchor/src/iosMain/kotlin/dev/kioba/anchor/AnchorContainer.kt`:

```kotlin
public class AnchorContainer<E, S> internal constructor(
  private val store: ViewModelStore,
  public val anchor: Anchor<E, S, *>,
) where E : Effect, S : ViewState {
  /**
   * Releases the anchor: clears the backing [ViewModelStore], which cancels
   * the ViewModel scope and every subscription/collection coroutine.
   * Call from your view's teardown (e.g. `deinit` or `.onDisappear`).
   */
  public fun clear() {
    store.clear()
  }
}

public fun <S, E> createAnchor(
  scope: (RememberAnchorScope) -> Anchor<E, S, *>,
  customKey: String? = null,
): AnchorContainer<E, S> where E : Effect, S : ViewState {
  // body = current rememberAnchor body, returning
  // AnchorContainer(storeOwner.viewModelStore, anchorScope.anchor)
}
```

Move the construction logic from `rememberAnchor` into `createAnchor`
unchanged (same factory, same `customKey` branch). Full KDoc on both,
including the retention story: "one container per logical screen; the caller
owns it and must call `clear()`".

**Verify**: `./gradlew :anchor:compileKotlinIosSimulatorArm64` → exit 0.

### Step 2: Deprecate `rememberAnchor`

Reimplement it as a thin delegate and mark it:

```kotlin
@Deprecated(
  message = "rememberAnchor neither retains nor disposes the anchor; every call " +
    "creates a runtime whose coroutines are never cancelled. Use createAnchor() " +
    "and call clear() from your view's teardown.",
  replaceWith = ReplaceWith("createAnchor(scope, customKey)"),
)
public fun <S, E> rememberAnchor(...): Anchor<E, S, *> ... =
  createAnchor(scope, customKey).anchor
```

Note: `allWarningsAsErrors` is enabled by the `kmp-library` convention plugin.
If the deprecated function's own delegation triggers a deprecation warning
loop or any new warning-as-error, suppress narrowly at the declaration site
(`@Suppress("DEPRECATION")` where needed) — do not turn off
`allWarningsAsErrors`.

**Verify**: `./gradlew :anchor:compileKotlinIosSimulatorArm64` → exit 0.

### Step 3: Tests

`anchor/src/iosTest/kotlin/dev/kioba/anchor/AnchorContainerTest.kt`:

1. `clear cancels state collection` — `createAnchor` a small fixture anchor
   (reuse the pattern of `commonTest` fixtures: a `TestState : ViewState`,
   `EmptyEffect`), start `anchor.nativeViewState().collect { ... }`
   (requires casting the anchor to `AnchorSink` — it is an `AnchorRuntime`
   at runtime; if the cast is awkward, collect `viewState` via the container's
   anchor through a typed helper), call `container.clear()`, then execute an
   action via the runtime and assert no further callbacks arrive.
   If direct execution post-clear is impossible to arrange cleanly, the
   minimum acceptable test is: `clear()` completes and a subsequent
   `NativeCancellable.cancel()` is safe (no crash).
2. `two createAnchor calls yield independent instances` — different state
   objects; mutating one does not affect the other.

If the `iosTest` source set does not compile out of the box, check how
`umbrella/src/iosTest/kotlin/dev/kioba/anchor/Test.ios.kt` is wired and mirror
it.

**Verify**: `./gradlew :anchor:iosSimulatorArm64Test` → exit 0, new tests pass.

### Step 4: Update the Swift sample (best effort)

In `iosApp/iosApp/ViewModelProtocol.swift`, switch from `rememberAnchor` to
`createAnchor`, hold the `AnchorContainer`, and call `container.clear()` in
`deinit` (alongside the existing `NativeCancellable.cancel()` calls). The
sample is not built by `./gradlew build`; if you cannot compile it (no
xcodebuild available), make the edit match the existing Swift style in that
file and note in your report that it is compile-unverified.

**Verify**: `./gradlew build` → exit 0 (Kotlin side unaffected by Swift edits).

## Test plan

See Step 3. There is no existing iosTest pattern in `:anchor` to model on —
that is part of what this plan establishes. Use kotlin.test + runBlocking-style
orchestration as in `anchor/src/commonTest/.../CancellableBasicTest.kt`.

## Done criteria

- [ ] `createAnchor`/`AnchorContainer` exist with KDoc; `rememberAnchor` is `@Deprecated`
- [ ] `./gradlew :anchor:iosSimulatorArm64Test` exits 0 with the new tests
- [ ] `./gradlew build` exits 0
- [ ] `iosApp` sample uses `createAnchor` + `clear()` (or report says why not)
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- `ViewModelStore`/`ViewModelProvider.create` APIs differ from the excerpt
  (lifecycle dependency version drift).
- An `iosTest` source set cannot be made to run for `:anchor` after mirroring
  the umbrella module's setup (report the Gradle error; tests may need to live
  in `umbrella` instead — advisor decision).
- Deprecating `rememberAnchor` breaks the published API check or some consumer
  in-repo other than `iosApp` (run `grep -rn "rememberAnchor" --include="*.kt" --include="*.swift" anchor features androidApp umbrella iosApp` first; only
  `iosMain` + `iosApp` should match).

## Maintenance notes

- This is the foundation for the "iOS DX parity" direction item (a Swift
  property-wrapper / observation layer would build on `AnchorContainer`).
- Reviewer: the deprecated path still leaks by design (it cannot not leak);
  the message must steer users firmly. Consider removal before 1.0.
- If SKIE is ever adopted, `NativeFlows.kt` and parts of `AnchorContainer`
  consumption become redundant — revisit then.

## Execution results (2026-09-29)

- **Status**: DONE. PR opened: https://github.com/kioba/anchor/pull/279 (branch `fix/005-ios-anchor-lifecycle`), stacked on `fix/024-init-signal-delivery` (#277 and its chain).
- **Drift**: `RememberAnchor.kt` had +4 lines since `492f7bc` (024's `nativeSignals()` KDoc). The `ViewModelStore`/`ViewModelProvider.create` APIs matched the excerpt. The only in-repo callers were `iosMain` and `iosApp`. No STOP condition was hit.
- **Changes**:
  - New `iosMain/AnchorContainer.kt` with `createAnchor(scope, customKey)` and `AnchorContainer<E, S>`. The container exposes `anchor`, `state`, `collectState`, `collectSignals` and `clear()`.
  - Collectors launch in the ViewModel scope on `Dispatchers.Main`, with a containment `CoroutineExceptionHandler`, so `clear()` stops them.
  - `collectSignals` collects `AnchorSink.signals`, 024's held-signal path.
  - `rememberAnchor` is `@Deprecated` and delegates to `createAnchor(...).anchor`, with `ReplaceWith("createAnchor(scope, customKey)")`.
  - The Swift sample holds the container and calls `clear()` in `deinit`.
- **Tests**:
  - New `iosTest/AnchorContainerTest.kt`, 8 tests. They run on the main thread and pump the main run loop so `Dispatchers.Main` work runs. They cover retention across GC, independence, cancellation of init and subscriptions (source `subscriptionCount` drops to 0), collectors stopping (`viewState` `subscriptionCount` drops to 0), an init-posted signal delivered once, collect-after-clear as a no-op, and GC reachability of cleared vs. uncleared containers.
  - Mutation checks: a no-op `clear()` fails 5 of 8; collectors in an independent scope fail 2 of 8.
  - Flakiness: 30/30 green runs of the test binary.
  - `./gradlew :anchor:iosSimulatorArm64Test`: 122 tests, 0 failures. `./gradlew build`: green.
- **Deviations**:
  - The container API is larger than Step 1's `anchor` + `clear()`. `NativeFlows` collectors own independent scopes, so without container-scoped collectors `clear()` could not stop collection, as the Step 1 KDoc claimed it would.
  - There are 8 tests instead of 2.
  - Branch name `fix/005-ios-anchor-lifecycle`, per the operator.
  - `plans/README.md` is not updated, because the operator maintains the index.
- **Pre-existing issue found**: `iosApp` doesn't compile on this base. Since #183, `ConfigAnchor` returns `Anchor<…, ConfigError>`, but `ViewModel(factory:)` needs `KotlinNothing` (`ConfigView.swift:5`). The sample builds (`xcodebuild`, simulator, no signing) with a temporary cast in `ConfigView`, which was not committed. That fix is left to plan 019 or a follow-up.
