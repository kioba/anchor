# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Anchor is a **state management architecture** for Kotlin Multiplatform applications built on Unidirectional Data Flow (UDF) design with Jetpack Compose integration. It targets Android, iOS (via iosX64, iosArm64, iosSimulatorArm64), and Desktop (JVM).

**Core philosophy**: Minimal, type-safe state management using receiver-based DSLs, immutable state updates, and Flow-based reactivity.

## Git Conventions

This project uses **gitmoji** for all commit messages and PR titles. Every message must start with the appropriate emoji:

| Emoji | Code | Usage |
|-------|------|-------|
| ✨ | `:sparkles:` | New feature |
| ♻️ | `:recycle:` | Refactor code |
| 🐛 | `:bug:` | Bug fix |
| ⬆️ | `:arrow_up:` | Upgrade dependency |
| 🔖 | `:bookmark:` | Version bump / release |
| 🧪 | `:test_tube:` | Add/update tests |
| 📝 | `:memo:` | Documentation |
| 🏗️ | `:building_construction:` | Architectural changes |
| 🔥 | `:fire:` | Remove code/files |
| 💚 | `:green_heart:` | Fix CI build |

See https://gitmoji.dev for the full list.

**Examples:**
- `✨ Add AnchorEffect composable`
- `♻️ Rename Effect type parameter E to R`
- `⬆️ Bump ui from 1.9.0 to 1.10.1`

## Build Commands

### Testing

```bash
# Run all tests across all platforms and generate aggregated report
./gradlew allTests

# Run tests for specific platforms
./gradlew desktopTest              # Desktop/JVM tests
./gradlew testAndroid               # Android unit tests (all variants)
./gradlew testDebugUnitTest         # Android debug unit tests only
./gradlew iosSimulatorArm64Test     # iOS simulator tests
./gradlew iosX64Test                # iOS x64 tests

# Run connected device tests (requires connected Android device/emulator)
./gradlew connectedAndroidTest      # All flavors
./gradlew connectedDebugAndroidTest # Debug build only
./gradlew connectedAndroidDeviceTest # Tests for androidMain on connected devices

# Run single test class (example for JVM)
./gradlew :anchor-test:desktopTest --tests "dev.kioba.anchor.test.CounterTest"
```

### Building

```bash
# Build all modules
./gradlew build

# Build specific modules
./gradlew :anchor:build           # Core library
./gradlew :anchor-test:build      # Test utilities
./gradlew :androidApp:build       # Sample Android app

# Assemble Android app
./gradlew :androidApp:assembleDebug
./gradlew :androidApp:assembleRelease
```

### Code Quality

```bash
# Run lint on default variant
./gradlew lint

# Run lint with auto-fix for safe suggestions
./gradlew lintFix

# Update lint baseline
./gradlew updateLintBaseline

# Run all checks (tests + lint)
./gradlew check
```

### Publishing

```bash
# Publish to Maven Central (requires credentials)
./gradlew publishToMavenCentral

# Publish to GitHub Packages (requires GPR credentials)
./gradlew publish
```

**Note**: Publishing requires credentials set via:
- `gpr.user` and `gpr.key` properties (GitHub Packages)
- Maven Central credentials via vaniktechMavenPublish plugin

## Architecture

### Core Abstractions

The architecture is built on a hierarchy of capabilities provided through interfaces:

```
Anchor<E, S>  (abstract base)
├── StateAnchor<S>          - Read-only state access via `state: StateFlow<S>`
├── MutableStateAnchor<S>   - State mutations via `reduce { copy(...) }`
├── EffectAnchor<E>         - Side effect execution with effect scope
├── CancellableAnchor<E,S>  - Cancellable task management (keyed jobs)
├── SubscriptionAnchor      - Event emission via `emit { ... }`
└── SignalAnchor            - One-time signals via `post { ... }`
    └── AnchorSink<E, S>    - Combines all above capabilities
        └── AnchorRuntime<E, S>  - Concrete implementation
```

**Implementation**: `AnchorRuntime` (`anchor/src/commonMain/kotlin/dev/kioba/anchor/AnchorRuntime.kt`) manages:
- State via `MutableStateFlow<S>`
- Signals via `MutableSharedFlow<SignalProvider>` (one-time UI events)
- Events via `MutableSharedFlow<Event>` (internal reactive stream)
- Coroutine jobs via `MutableMap<Any, Job>` (cancellable operations)

### Marker Interfaces

Four empty marker interfaces provide type safety (`anchor/src/commonMain/kotlin/dev/kioba/anchor/AnchorMarkers.kt`):

- `ViewState` - UI state data classes
- `Effect` - External dependencies/side effect scope
- `Signal` - One-time notifications (toasts, navigation, etc.)
- `Event` - Internal event stream for subscriptions

### Data Flow Pattern

```
User Action (Button Click)
    ↓
anchor(CounterAnchor::increment)  // Type-safe callback via CompositionLocal
    ↓
AnchorScope.execute { anchor.increment() }  // Coroutine launch (Dispatchers.Default)
    ↓
reduce { copy(count = count.inc()) }  // Immutable state update
    ↓
MutableStateFlow.update()  // Flow emission
    ↓
collectAsState() in RememberAnchor  // Recomposition trigger
    ↓
UI Recomposition
```

**Parallel flows**:
- `post { Signal }` → `MutableSharedFlow<SignalProvider>` → `HandleSignal` → LaunchedEffect
- `emit { Event }` → `MutableSharedFlow<Event>` → Subscription chains
- `effect { }` → Coroutine execution with E (Effect) receiver

### Compose Integration

**Primary composable**: `RememberAnchor` (`anchor/src/androidMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt`)

Responsibilities:
1. Creates/retrieves ViewModel-scoped `AnchorRuntime` via `ContainerViewModel`
2. Collects `state: StateFlow<S>` as `State<S>` (Main.immediate dispatcher)
3. Collects `signals: StateFlow<SignalProvider>` as `State<SignalProvider>`
4. Provides `AnchorScope` via `LocalAnchor` CompositionLocal
5. Provides `SignalProvider` via `LocalSignals` CompositionLocal

**Action creation**: Four overloaded `anchor()` composables (`AnchorAction.kt`) create type-safe callbacks:
```kotlin
anchor(CounterAnchor::increment)              // () -> Unit
anchor(CounterAnchor::updateById)             // (Int) -> Unit
anchor(CounterAnchor::updateByIdAndName)      // (Int, String) -> Unit
anchor(CounterAnchor::updateByIdNameAndAge)   // (Int, String, Int) -> Unit
```

Implementation retrieves `LocalAnchor.current` and returns a callback that calls `scope.execute(block)`.

**Signal handling**: `HandleSignal<T>` composable (`LocalSignal.kt`) triggers LaunchedEffect when signal of type T is emitted.

### Function Receivers (Not Context Receivers)

Despite the README mentioning context receivers, the codebase uses **function receivers** (extension functions):

```kotlin
// Actions are extension functions on the Anchor type
suspend fun CounterAnchor.increment() {
  reduce { copy(count = count.inc()) }
  post { CounterSignal.Increment }
}

// Used via anchor() which provides the receiver implicitly
onClick = anchor(CounterAnchor::increment)
```

The `AnchorScope<E, S>` is a `fun interface` with SAM conversion:
```kotlin
fun interface AnchorScope<out E : Effect, out S : ViewState> {
  fun execute(block: suspend Anchor<@UnsafeVariance E, @UnsafeVariance S>.() -> Unit)
}
```

### Subscriptions & Reactive Chains

**File**: `anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt`

Subscriptions set up reactive event handling chains:

```kotlin
suspend fun MainSubScope.subscriptions() {
  listen(::refresh)  // Connect event flow to handler
}

internal fun MainSubScope.refresh(flow: Flow<MainEvent>): Flow<Int> =
  flow
    .filter { it is MainEvent.Refresh || it is MainEvent.Cancel }
    .flatMapLatest { mainEvent ->
      when (mainEvent) {
        MainEvent.Refresh -> effect.fetchData()
        MainEvent.Cancel -> emptyFlow()
      }
    }
    .anchor(MainAnchor::updateCounter)  // Apply result to anchor
```

**Lifecycle**:
- `init` block: Runs on scope initialization
- `subscriptions` block: Sets up reactive chains (via `anchor.subscribe()` in ContainerViewModel)

### Testing Infrastructure

**Module**: `anchor-test` provides BDD-style testing DSL

**Core function**: `runAnchorTest` (`anchor-test/src/commonMain/kotlin/dev/kioba/anchor/test/AnchorTest.kt`)

```kotlin
runAnchorTest(RememberAnchorScope::counterAnchor) {
  given("the screen started") {
    initialState { CounterState(count = -1) }
  }

  on("incrementing the counter", CounterAnchor::increment)

  verify("the state updated correctly") {
    assertState { copy(count = 0) }
    assertSignal { CounterSignal.Increment }
  }
}
```

**Test scopes**:
- `GivenScope<E, S>` - Setup initial state, effect scope, preconditions
- `VerifyScope<E, S>` - Assert state changes, signals, events, effects
- `AnchorTestScope<E, S>` - Orchestrator connecting given/on/verify

**Test runtime** (`AnchorTestRuntime.kt`) records all actions (reduce, signal, emit, effect) for deterministic verification.

## Module Structure

```
anchor/                      # Core library (publishable)
├── commonMain/             # Multiplatform code
│   ├── Anchor.kt           # Interface hierarchy
│   ├── AnchorRuntime.kt    # Concrete implementation
│   ├── AnchorScope.kt      # UI-to-Anchor bridge
│   ├── SubscriptionDsl.kt  # Reactive event handling
│   └── viewmodel/          # ViewModel integration
└── androidMain/            # Android/Compose specific
    └── compose/
        ├── RememberAnchor.kt    # Main composable
        ├── AnchorAction.kt      # Action creation
        ├── LocalScope.kt        # AnchorScope CompositionLocal
        └── LocalSignal.kt       # Signal handling

anchor-test/                 # Test utilities (publishable)
└── commonMain/
    ├── AnchorTest.kt        # BDD test DSL
    └── scopes/              # Test scope implementations

features/                    # Example feature modules
├── counter/                 # Simple counter example
├── config/                  # Configuration example
└── main/                    # Complex example with subscriptions

androidApp/                  # Sample Android application
```

## Development Patterns

### Creating a New Feature

1. **Define state and markers**:
```kotlin
data class MyState(val value: Int = 0) : ViewState
class MyEffect : Effect
sealed interface MySignal : Signal {
  data object Success : MySignal
}
sealed interface MyEvent : Event {
  data object Refresh : MyEvent
}
```

2. **Create anchor factory**:
```kotlin
fun RememberAnchorScope.myAnchor(): Anchor<MyEffect, MyState> =
  create(
    initialState = ::MyState,
    effectScope = { MyEffect() },
    init = { /* Optional: initialize */ },
    subscriptions = { /* Optional: reactive chains */ }
  )
```

3. **Define actions as extension functions**:
```kotlin
suspend fun MyAnchor.increment() {
  reduce { copy(value = value + 1) }
  post { MySignal.Success }
}

suspend fun MyAnchor.loadData() {
  cancellable(key = "load") {
    val result = effect { fetchFromApi() }
    reduce { copy(value = result) }
  }
}
```

4. **Use in Compose UI**:
```kotlin
@Composable
fun MyFeature() {
  RememberAnchor(scope = { myAnchor() }) { state ->
    HandleSignal<MySignal> { signal ->
      // Handle one-time events
    }

    Button(onClick = anchor(MyAnchor::increment)) {
      Text("Count: ${state.value}")
    }
  }
}
```

### Testing Pattern

```kotlin
class MyFeatureTest {
  @Test
  fun testIncrement() = runAnchorTest(RememberAnchorScope::myAnchor) {
    given("initial state") {
      initialState { MyState(value = 0) }
    }

    on("incrementing", MyAnchor::increment)

    verify("state incremented") {
      assertState { copy(value = 1) }
      assertSignal { MySignal.Success }
    }
  }
}
```

### Effect Scope Pattern

Effects are external dependencies passed to actions:

```kotlin
class MyEffect(
  val apiClient: ApiClient,
  val database: Database,
  val analytics: Analytics
) : Effect

suspend fun MyAnchor.loadUser(id: Int) {
  val user = effect { apiClient.getUser(id) }  // `this` is MyEffect
  reduce { copy(user = user) }
}
```

### Cancellable Operations

Use `cancellable` with a key to auto-cancel previous operations:

```kotlin
suspend fun SearchAnchor.search(query: String) {
  cancellable(key = "search") {
    delay(300) // debounce
    val results = effect { api.search(query) }
    reduce { copy(results = results) }
  }
}
```

Each new `search()` call cancels the previous job with key `"search"`.

## Important Notes

### Type Safety

- Actions are **type-safe extension functions** on specific Anchor types
- `anchor()` composable performs safe casting (guaranteed by RememberAnchor providing correct anchor)
- Use function references (`::increment`) over lambdas for better IDE support

### Immutability

- **Always** use `reduce { copy(...) }` for state updates
- Never mutate state directly
- Reducers must be pure (same input → same output)

### Multiplatform Considerations

- Core logic in `commonMain` is platform-agnostic
- Compose integration in `androidMain` is Android-specific
- iOS integration uses SKIE plugin for Swift-friendly APIs
- Desktop uses JVM target with standard Compose Desktop

### Coroutine Dispatchers

- Actions execute on `Dispatchers.Default` (via `AnchorScope.execute`)
- State collection on `Main.immediate` (via `RememberAnchor`)
- Effects default to `Dispatchers.IO` (configurable per effect call)
- ViewModelScope manages lifecycle

### Current Version

Published version: `0.0.8` (see `anchor/build.gradle.kts:102`)

## Common Issues

### "Unused Kotlin Source Sets" Warning

The warning about `androidInstrumentedTest` and `androidUnitTest` is expected due to the multiplatform library setup using `androidLibrary` DSL. Source sets are properly configured via `withDeviceTestBuilder` and `withHostTestBuilder`.

### Explicit API Mode

The library uses `kotlin { explicitApi() }`, requiring all public APIs to have explicit visibility modifiers and return types.

### GitHub Packages Authentication

If you encounter authentication issues with GitHub Packages, set:
- `gpr.user` (GitHub username) and `gpr.key` (Personal Access Token) in `~/.gradle/gradle.properties`
- Or use environment variables `USERNAME` and `TOKEN`
