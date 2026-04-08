# API Reference

This page provides a detailed reference for the public API of the Anchor library.

## Core API

### `Anchor<E, S>`

The main interface for state management.

- `state: S`: The current state.
- `reduce(reducer: S.() -> S)`: Updates the state.
- `effect(coroutineContext, block: suspend E.() -> R): R`: Executes a side effect.
- `cancellable(key, block)`: Executes a block that can be cancelled by a key.
- `post(block: SignalScope.() -> Signal)`: Posts a signal to the UI.
- `emit(block: SubscriptionScope.() -> Event)`: Emits an internal event.

---

### `RememberAnchorScope.create(...)`

Creates an `Anchor` instance.

- `initialState`: Factory for the initial state.
- `effectScope`: Factory for effect dependencies.
- `init`: Optional initialization block.
- `subscriptions`: Optional subscription setup block.

---

## Compose API (Android)

### `RememberAnchor(scope, customKey, content)`

Sets up Anchor in a Composable.

- `scope`: Factory block to create the Anchor.
- `customKey`: Optional key for state retention.
- `content`: Composable block receiving `AnchorStateScope`.

---

### `AnchorStateScope<S>`

Scope available inside `RememberAnchor`.

- `state`: Accesses the current state (triggers recomposition for any change).
- `collectState(selector: (S) -> T): T`: Observes a specific part of the state (granular recomposition).

---

### `anchor(block)`

Helper to create UI action callbacks.

- `anchor(suspend A.() -> Unit): () -> Unit`
- `anchor(suspend A.(I) -> Unit): (I) -> Unit`
- `anchor(suspend A.(I, O) -> Unit): (I, O) -> Unit`

---

### `HandleSignal<T>(block)`

Handles one-time signals of type `T`.

```kotlin
HandleSignal<MySignal> { signal ->
    // Handle the signal
}
```

---

### `PreviewAnchor(state, content)`

A utility for Compose Previews.

```kotlin
@Preview
@Composable
fun MyPreview() {
    PreviewAnchor(state = MyState()) {
        MyUi()
    }
}
```
