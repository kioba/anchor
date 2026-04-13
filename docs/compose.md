# Compose Integration

The `anchor-compose` module provides Jetpack Compose bindings for Anchor. It connects the Anchor state machine to the Compose lifecycle, giving you type-safe state observation, action dispatching, and signal handling — all scoped to a ViewModel.

**Why a separate module?** Keeping Compose dependencies out of the core `anchor` module means your shared business logic stays free of UI framework dependencies. This is especially important for Kotlin Multiplatform projects where not every target uses Compose.

## Installation

```kotlin
dependencies {
    implementation("{{ group_id }}:anchor-compose:{{ version }}")
}
```

!!! note
    `anchor-compose` transitively includes the core `anchor` module, so you don't need to add both.

---

## RememberAnchor

`RememberAnchor` is the primary composable that sets up an Anchor-powered screen. It creates a ViewModel-scoped Anchor instance, collects state, and provides the necessary CompositionLocals for `anchor()` and `HandleSignal`.

```kotlin
@Composable
fun CounterScreen() {
    RememberAnchor(scope = { counterAnchor() }) {
        val count = collectState { it.count }

        Text("Count: $count")
        Button(onClick = anchor(CounterAnchor::increment)) {
            Text("+")
        }
    }
}
```

### Parameters

| Parameter   | Description                                                                 |
|-------------|-----------------------------------------------------------------------------|
| `scope`     | Factory that creates the Anchor instance (called once per ViewModel)        |
| `customKey` | Optional key for ViewModel storage (defaults to the ViewState class name)   |
| `content`   | Composable block receiving `AnchorStateScope<S>`                            |

### Lifecycle

1. On first composition, `RememberAnchor` creates a `ContainerViewModel` that holds the `AnchorRuntime`
2. The Anchor's `init` block runs once, and `subscriptions` are set up
3. State is collected via `collectAsStateWithLifecycle()` on `Main.immediate`
4. The ViewModel survives configuration changes — state is retained automatically

---

## Observing State

Inside `RememberAnchor`, you have access to `AnchorStateScope<S>` which provides two ways to observe state:

### `state` — Full state access

```kotlin
RememberAnchor(scope = { counterAnchor() }) {
    Text("Count: ${state.count}")
}
```

Any change to the state triggers recomposition of the entire content block.

### `collectState` — Granular recomposition

```kotlin
RememberAnchor(scope = { counterAnchor() }) {
    val count = collectState { it.count }
    val isLoading = collectState { it.isLoading }

    // Only recomposes when the selected value changes,
    // not when other state fields change.
}
```

`collectState` applies a selector function and only triggers recomposition when the selected value changes. Prefer this for screens with many state fields.

---

## Dispatching Actions

The `anchor()` composable creates type-safe callbacks from Anchor action functions. It supports 0 to 3 parameters:

```kotlin
// No parameters — returns () -> Unit
Button(onClick = anchor(CounterAnchor::increment)) {
    Text("+")
}

// One parameter — returns (I) -> Unit
TextField(onValueChange = anchor(ConfigAnchor::updateText))

// Two parameters — returns (I, O) -> Unit
CustomSlider(onChange = anchor(SettingsAnchor::updateRange))
```

Actions are executed asynchronously on `Dispatchers.Default` within the ViewModel's coroutine scope.

---

## Handling Signals

Signals are one-time events (navigation, snackbars, toasts) that shouldn't persist in state. Use `HandleSignal` to react to them:

```kotlin
RememberAnchor(scope = { counterAnchor() }) {
    HandleSignal<CounterSignal> { signal ->
        when (signal) {
            CounterSignal.Increment -> snackbarHostState.showSnackbar("Incremented!")
            CounterSignal.Decrement -> snackbarHostState.showSnackbar("Decremented!")
        }
    }

    // ... UI content
}
```

`HandleSignal` uses `LaunchedEffect` internally — it respects the composable lifecycle and automatically stops collecting when the composable leaves the composition.

---

## Compose Previews

Use `PreviewAnchor` to provide static state for `@Preview` composables without needing a full Anchor setup:

```kotlin
@Preview
@Composable
fun CounterPreview() {
    PreviewAnchor(state = CounterState(count = 42)) {
        val count = collectState { it.count }
        Text("Count: $count")
    }
}
```

`PreviewAnchor` wraps the state in an `AnchorStateScope` so your content composable works identically to production. Actions dispatched via `anchor()` become no-ops in previews.

---

## Full Example

Putting it all together:

```kotlin
// State
data class CounterState(
    val count: Int = 0,
) : ViewState

// Signals
sealed interface CounterSignal : Signal {
    data object Increment : CounterSignal
    data object Decrement : CounterSignal
}

// Anchor factory
typealias CounterAnchor = Anchor<EmptyEffect, CounterState>

fun RememberAnchorScope.counterAnchor(): CounterAnchor =
    create(initialState = ::CounterState, effectScope = { EmptyEffect })

// Actions
suspend fun CounterAnchor.increment() {
    reduce { copy(count = count + 1) }
    post { CounterSignal.Increment }
}

suspend fun CounterAnchor.decrement() {
    reduce { copy(count = count - 1) }
    post { CounterSignal.Decrement }
}

// UI
@Composable
fun CounterScreen(snackbarHostState: SnackbarHostState) {
    RememberAnchor(scope = { counterAnchor() }) {
        HandleSignal<CounterSignal> { signal ->
            val message = when (signal) {
                CounterSignal.Increment -> "Incremented"
                CounterSignal.Decrement -> "Decremented"
            }
            snackbarHostState.showSnackbar(message)
        }

        val count = collectState { it.count }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
            )
            Row {
                Button(onClick = anchor(CounterAnchor::decrement)) { Text("-") }
                Button(onClick = anchor(CounterAnchor::increment)) { Text("+") }
            }
        }
    }
}

// Preview
@Preview
@Composable
fun CounterPreview() {
    PreviewAnchor(state = CounterState(count = 10)) {
        val count = collectState { it.count }
        Text("Count: $count")
    }
}
```
