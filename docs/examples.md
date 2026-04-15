# Examples

## Comprehensive Counter Example

This example demonstrates state, actions, and signals.

### 1. State and Signals

```kotlin
data class CounterState(
    val count: Int = 0,
    val isLoading: Boolean = false
) : ViewState

sealed interface CounterSignal : Signal {
    data class ShowToast(val message: String) : CounterSignal
}
```

### 2. Anchor Definition

```kotlin
class CounterEffect : Effect

typealias CounterAnchor = Anchor<CounterEffect, CounterState, Nothing>

fun RememberAnchorScope.counterAnchor(): CounterAnchor =
    create(
        initialState = ::CounterState,
        effectScope = { CounterEffect() }
    )
```

### 3. Actions

```kotlin
fun CounterAnchor.increment() {
    reduce { copy(count = count + 1) }
}

fun CounterAnchor.decrement() {
    reduce { copy(count = count - 1) }
}

suspend fun CounterAnchor.reset() {
    reduce { copy(isLoading = true) }
    delay(1000) // Simulate work
    reduce { copy(count = 0, isLoading = false) }
    post { CounterSignal.ShowToast("Counter reset!") }
}
```

### 4. UI Implementation

```kotlin
@Composable
fun CounterPage() {
    RememberAnchor(scope = { counterAnchor() }) {
        // Handle signals
        HandleSignal<CounterSignal.ShowToast> { signal ->
            // Show toast/snackbar
        }

        val count = collectState { it.count }
        val isLoading = collectState { it.isLoading }

        Column {
            Text("Count: $count")

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Row {
                    Button(onClick = anchor(CounterAnchor::increment)) {
                        Text("+")
                    }
                    Button(onClick = anchor(CounterAnchor::decrement)) {
                        Text("-")
                    }
                    Button(onClick = anchor(CounterAnchor::reset)) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}
```
