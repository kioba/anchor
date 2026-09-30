# Core Concepts

Anchor is based on a few core concepts that work together to manage state and side effects in a predictable way.

## Overview

The Anchor architecture consists of the following main components:

- **ViewState**: The immutable representation of the UI state.
- **Effect**: Dependencies required for side effects (e.g., repositories, services).
- **Anchor**: The engine that manages state, executes effects, and handles communication.
- **Actions**: Functions that define how to modify state or trigger side effects.
- **Signals**: One-time messages sent from the Anchor to the UI.
- **Events**: Internal messages within the Anchor used for complex logic.

---

## ViewState

`ViewState` is a marker interface for your UI state. It should typically be an immutable `data class`.

```kotlin
data class MyState(
    val isLoading: Boolean = false,
    val items: List<String> = emptyList(),
    val error: String? = null
) : ViewState
```

## Effect

`Effect` is a marker interface for dependencies used in side effects. This allows you to keep your Anchor logic pure and testable by injecting dependencies.

```kotlin
class MyEffect(
    val repository: MyRepository
) : Effect
```

## Anchor

The `Anchor` is the central component. It manages the `ViewState` and provides a DSL for:

- **reduce**: Updating the state.
- **effect**: Executing suspendable side effects.
- **cancellable**: Managing long-running jobs that can be cancelled.
- **post**: Sending signals to the UI.
- **emit**: Emitting internal events.

`Anchor<R, S, Err>` carries three type parameters: `R` (Effect), `S` (ViewState), and `Err` (domain error type). Use `PureAnchor<R, S>` when your anchor has no domain errors. See [Error Handling](errors.md) for the full error handling guide.

## Actions

Actions are usually extension functions on your specific `Anchor` type. They encapsulate the business logic.

```kotlin
suspend fun MyAnchor.loadData() {
    reduce { copy(isLoading = true) }
    try {
        val result = effect { repository.fetchData() }
        reduce { copy(isLoading = false, items = result) }
    } catch (e: Exception) {
        reduce { copy(isLoading = false, error = e.message) }
    }
}
```

## Signals

`Signal`s are one-time messages for things that aren't part of the persistent state, like showing a Snackbar, a Toast, or navigating.

```kotlin
sealed interface MySignal : Signal {
    data class ShowError(val message: String) : MySignal
}

// In an Action:
post { MySignal.ShowError("Something went wrong") }

// In UI:
HandleSignal<MySignal.ShowError> { signal ->
    snackbarHostState.showSnackbar(signal.message)
}
```

### Delivery guarantees

A posted signal is delivered to every collector that is attached and accepts it at the time of posting. If no attached collector accepts it, the signal is **held**, up to 64 signals, dropping the oldest, and is delivered **once** to the first accepting collector that attaches afterwards. Delivered signals are never replayed. A signal already handed to a collector that is cancelled before processing it is lost.

- `HandleSignal<T>` accepts the signals of type `T`. A signal posted from `init` before the UI collects, or while the UI is stopped, waits for the first handler of its type. See [Compose Integration](compose.md).
- An accept-all collector, such as `nativeSignals()` on iOS or a collector of `AnchorSink.signals`, accepts every signal, so it also claims held signals of any type.
- Held signals live in memory only: process death loses them. Anything the user must still see after that belongs in state.

## Events & Subscriptions

`Event`s are internal messages. They are useful for complex logic where one action triggers another, or for reacting to lifecycle events like `Created`.

```kotlin
subscriptions = {
    connect<Created> { events ->
        events.anchor { loadData() }
    }
}
```
