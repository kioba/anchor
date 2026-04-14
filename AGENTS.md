# Anchor

Anchor is a state management architecture for Kotlin Multiplatform with Jetpack Compose integration. It uses Unidirectional Data Flow (UDF) with immutable state, receiver-based DSLs, and Flow-based reactivity.

- **Repository**: https://github.com/kioba/anchor
- **Docs**: https://kioba.github.io/anchor/
- **Published version**: 0.1.0
- **Platforms**: Android, iOS (iosX64, iosArm64, iosSimulatorArm64), Desktop (JVM)

## Modules

```kotlin
dependencies {
    implementation("dev.kioba.anchor:anchor:0.1.1")        // Core state management
    implementation("dev.kioba.anchor:anchor-compose:0.1.0") // Compose bindings
    testImplementation("dev.kioba.anchor:anchor-test:0.1.0") // Testing DSL
}
```

## Architecture

The core abstraction is `Anchor<R, S>` where `R : Effect` (dependencies) and `S : ViewState` (state). It provides:

- `reduce { copy(...) }` — Immutable state updates
- `effect { dependency.call() }` — Side effects with injected dependencies
- `cancellable(key) { ... }` — Auto-cancelling keyed operations
- `post { MySignal }` — One-time UI signals (navigation, toasts)
- `emit { MyEvent }` — Internal events for subscription chains

## Creating a Feature

### 1. Define state and markers

```kotlin
data class MyState(val value: Int = 0) : ViewState

class MyEffect(val api: ApiClient) : Effect

sealed interface MySignal : Signal {
    data object Success : MySignal
}
```

### 2. Create anchor factory

```kotlin
typealias MyAnchor = Anchor<MyEffect, MyState>

fun RememberAnchorScope.myAnchor(): MyAnchor =
    create(
        initialState = ::MyState,
        effectScope = { MyEffect(api = ApiClient()) },
        init = { /* runs once on creation */ },
        subscriptions = { /* reactive event chains */ }
    )
```

### 3. Define actions as extension functions

```kotlin
suspend fun MyAnchor.loadData() {
    reduce { copy(isLoading = true) }
    val result = effect { api.fetchData() }
    reduce { copy(value = result, isLoading = false) }
    post { MySignal.Success }
}

suspend fun MyAnchor.search(query: String) {
    cancellable(key = "search") {
        delay(300) // debounce
        val results = effect { api.search(query) }
        reduce { copy(results = results) }
    }
}
```

### 4. Compose UI

```kotlin
@Composable
fun MyScreen() {
    RememberAnchor(scope = { myAnchor() }) {
        HandleSignal<MySignal> { signal -> /* handle one-time events */ }

        val value = collectState { it.value }  // granular recomposition

        Button(onClick = anchor(MyAnchor::loadData)) {
            Text("Load: $value")
        }
    }
}
```

### 5. Test

```kotlin
@Test
fun `loadData updates state and posts signal`() {
    runAnchorTest(RememberAnchorScope::myAnchor) {
        given("initial state") {
            initialState { MyState(value = 0) }
            effectScope { MyEffect(api = FakeApi()) }
        }

        on("loading data", MyAnchor::loadData)

        verify("state updated and signal posted") {
            assertState { copy(isLoading = true) }
            assertState { copy(value = 42, isLoading = false) }
            assertSignal { MySignal.Success }
        }
    }
}
```

## Key Compose APIs

| API | Purpose |
|-----|---------|
| `RememberAnchor(scope) { }` | Sets up ViewModel-scoped Anchor with state collection |
| `collectState { it.field }` | Granular state observation (preferred over `state`) |
| `anchor(MyAnchor::action)` | Type-safe callback for UI events (0-3 params) |
| `HandleSignal<T> { }` | Handles one-time signals (navigation, toasts) |
| `PreviewAnchor(state) { }` | Static state for `@Preview` composables |

## Build & Test

```bash
./gradlew allTests           # All platforms
./gradlew desktopTest        # Desktop/JVM
./gradlew testDebugUnitTest  # Android debug
./gradlew check              # Tests + lint
./gradlew build              # Build all modules
```

## Constraints

- **Immutability**: Always use `reduce { copy(...) }` for state updates. Never mutate state directly.
- **Explicit API**: The library uses `kotlin { explicitApi() }` — all public APIs need explicit visibility modifiers and return types.
- **Dispatchers**: Actions run on `Dispatchers.Default`. State collection on `Main.immediate`. Effects default to `Dispatchers.IO`.
- **Type safety**: Actions are extension functions on specific Anchor types. Use function references (`::increment`) over lambdas.
