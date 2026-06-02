# API Reference

This page provides a detailed reference for the public API of the Anchor library.

## Core API

### `Anchor<R, S, Err>`

The main abstract class for state management. `R` is the Effect type, `S` is the ViewState type, and `Err` is the domain error type (use `Nothing` when no domain errors are needed).

- `state: S`: The current state.
- `reduce(reducer: S.() -> S)`: Updates the state.
- `effect(coroutineContext, block: suspend R.() -> T): T`: Executes a side effect.
- `cancellable(key, block)`: Executes a block that can be cancelled by a key.
- `post(block: SignalScope.() -> Signal)`: Posts a signal to the UI.
- `emit(block: SubscriptionScope.() -> Event)`: Emits an internal event.

### `PureAnchor<R, S>`

A convenience typealias for `Anchor<R, S, Nothing>` — use when your anchor has no domain errors.

---

### `RememberAnchorScope.create(...)`

Creates an `Anchor` instance.

- `initialState`: Factory for the initial state.
- `effectScope`: Factory for effect dependencies.
- `init`: Optional initialization block.
- `subscriptions`: Optional subscription setup block.
- `onDomainError`: Optional callback invoked when a domain error is raised (defaults to `null`).
- `defect`: Optional callback invoked when an unexpected error occurs (defaults to `null`).

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

---

## Error Handling API

See the [Error Handling](errors.md) guide for a full walkthrough with examples. The key types are summarised below.

### `Raise<Err>`

Interface mixed into `Anchor<R, S, Err>`. Provides:

- `raise(error: Err): Nothing` — short-circuits the current action; control passes to `onDomainError`.
- `ensure(condition: Boolean, error: () -> Err)` — shorthand for `if (!condition) raise(error())`.
- `Recover<Err, T>.getOrRaise(): T` — unwraps a `Recover.Ok` or re-raises its `Recover.Error`.

### `Recover<Err, T>`

Sealed return type for the `recover { }` block:

- `Recover.Ok(value: T)` — the block completed without raising.
- `Recover.Error(error: Err)` — the block called `raise()`.

Helpers: `getOrNull()`, `getErrorOrNull()`, `getOrElse { }`, `fold(onError, onOk)`.

### `recover { }` (standalone)

```kotlin
val result: Recover<MyError, String> = recover {
    ensure(input.isNotBlank()) { MyError.Empty }
    input.trim()
}
```

Catches any `raise()` inside the block and returns a `Recover` instead of propagating to `onDomainError`.

### `ErrorScope<R, S>`

Typealias for `BaseAnchorScope<R, S>`. Used as the receiver in `onDomainError` and `defect` handlers. Provides `reduce`, `effect`, `post`, and `emit` — intentionally omits `raise` and `orDie` to prevent re-raising from handlers.

### `DefectAnchor<Err>` / `orDie(error)`

- `orDie(error: Err): Nothing` — escalates a domain error to the `defect` handler, bypassing `onDomainError`. Use for programmer errors and broken invariants, not user-facing validation.
