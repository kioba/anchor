# Error Handling

This page explains how Anchor handles domain errors and unexpected failures in actions.

---

## When you don't need this page

If your feature never has business-rule violations — no validation, no failed API calls you want to surface as typed errors — you don't need any of this. Use `PureAnchor<R, S>` (a typealias for `Anchor<R, S, Nothing>`) and ignore the `Err` type parameter entirely.

```kotlin
class CounterEffect : Effect
typealias CounterAnchor = PureAnchor<CounterEffect, CounterState>

fun RememberAnchorScope.counterAnchor(): CounterAnchor =
    create(
        initialState = ::CounterState,
        effectScope = { CounterEffect() },
    )
```

The type system makes `raise()` and `orDie()` statically uncallable on a `PureAnchor`. Keep reading only if you need typed domain errors.

---

## Declaring your error type

Define a sealed interface for your domain errors and pass it as the third type parameter to `Anchor<R, S, Err>`.

```kotlin
sealed interface ConfigError {
    data object EmptyInput : ConfigError
    data class TooLong(val maxLength: Int) : ConfigError
}

typealias ConfigAnchor = Anchor<ConfigEffect, ConfigState, ConfigError>
```

Sealed interfaces work well here because `when` expressions on them are exhaustive — the compiler tells you if you miss a case.

---

## Registering handlers in `create()`

Pass `onDomainError` and `defect` to `create()` to decide what happens when an error reaches the top of the action stack.

```kotlin
fun RememberAnchorScope.configAnchor(): ConfigAnchor =
    create(
        initialState = ::ConfigState,
        effectScope = { ConfigEffect() },
        onDomainError = { error ->
            when (error) {
                ConfigError.EmptyInput ->
                    reduce { copy(errorMessage = "Text cannot be empty") }
                is ConfigError.TooLong ->
                    reduce { copy(errorMessage = "Text exceeds ${error.maxLength} characters") }
            }
        },
        defect = { t ->
            reduce { copy(errorMessage = "Unexpected error: ${t.message}") }
        },
    )
```

The handler lambda receives `ErrorScope<R, S>` as its receiver. That scope provides `reduce`, `effect`, `post`, and `emit` — but intentionally **not** `raise` or `orDie`. Error handlers cannot re-raise; they are terminal.

Both handlers are optional. If you omit `onDomainError`, an unhandled `raise()` will crash the coroutine. If you omit `defect`, unexpected exceptions propagate normally.

---

## Strategy 1 — Propagate with `raise()` and `ensure()`

Call `raise(error)` inside an action to short-circuit execution. Control jumps immediately to `onDomainError`. This is the right strategy when the error should be shown to the user and you want the handler to decide what the UI looks like.

```kotlin
suspend fun ConfigAnchor.updateText(text: String) {
    ensure(text.isNotBlank()) { ConfigError.EmptyInput }
    ensure(text.length <= 100) { ConfigError.TooLong(maxLength = 100) }
    withContext(Dispatchers.Default) {
        delay(1000)
        reduce { copy(text = text.trim(), errorMessage = null) }
    }
}
```

`ensure(condition) { error }` is shorthand for `if (!condition) raise(error)`. When either guard fails, the action stops and `onDomainError` runs with the corresponding `ConfigError`.

---

## Strategy 2 — Recover locally with `recover { }`

Wrap a block in `recover { }` to catch any `raise()` inside it and turn the outcome into a `Recover<Err, T>` value that you handle inline. The error never reaches `onDomainError`.

```kotlin
suspend fun ConfigAnchor.updateTextClamped(text: String) {
    val result = recover {
        ensure(text.isNotBlank()) { ConfigError.EmptyInput }
        ensure(text.length <= 100) { ConfigError.TooLong(maxLength = 100) }
        text.trim()
    }
    val validated: String = when (result) {
        is Recover.Ok -> result.value
        is Recover.Error -> when (val err = result.error) {
            is ConfigError.TooLong -> text.take(100).trim()  // silently clamp
            ConfigError.EmptyInput -> return                 // nothing to do
        }
    }
    withContext(Dispatchers.Default) {
        delay(1000)
        reduce { copy(text = validated, errorMessage = null) }
    }
}
```

`Recover` has two variants: `Recover.Ok(value)` and `Recover.Error(error)`. Useful helpers on it:

| Helper | Returns |
|--------|---------|
| `getOrNull()` | `T?` — value or `null` |
| `getErrorOrNull()` | `Err?` — error or `null` |
| `getOrElse { fallback }` | `T` — value or fallback |
| `fold(onError, onOk)` | `V` — transform either branch |
| `getOrRaise()` | `T` — value or re-raises the error |

Use local recovery when you want to apply a business rule silently (clamp, default, skip) without surfacing the error in the UI.

---

## Strategy 3 — Escalate with `orDie()`

`orDie(error)` escalates a domain error to the `defect` handler, bypassing `onDomainError`. Use it for programmer mistakes — broken invariants, unexpected `null`s — where the error is not something a user can fix.

```kotlin
suspend fun MyAnchor.processItem(id: Int) {
    val item = effect { repository.findById(id) }
        ?: orDie(MyError.ItemNotFound(id))  // should never happen in production
    reduce { copy(current = item) }
}
```

!!! warning "Domain error vs defect"
    `raise()` is for expected, recoverable errors (validation failures, network errors a retry might fix).
    `orDie()` is for unexpected failures that indicate a bug.
    The `defect` handler runs for `orDie()` calls **and** for any uncaught `Throwable` from your action.

---

## The `ErrorScope` constraint

Inside both `onDomainError` and `defect`, your receiver is `ErrorScope<R, S>` — a type alias for `BaseAnchorScope<R, S>`. This scope provides:

- `reduce { }` — update state
- `effect { }` — run side effects
- `post { }` — emit a signal
- `emit { }` — emit an event

It deliberately omits `raise()` and `orDie()`. This is an architectural constraint: error handlers are final. A handler that could itself raise would create unbounded recursion. If you need conditional logic in a handler, express it with `if`/`when` and calls to `reduce` or `post`.

---

## Cancellable interactions

When an action uses `cancellable(key) { ... }` and a `raise()` fires inside the cancellable block, Anchor records the `RaisedException` and propagates it after the coroutine job completes. This means `onDomainError` is still invoked, but only once the cancellable scope has fully wound down. The handler sees the same error as if it had been raised outside a cancellable block.

---

## Choosing a strategy

| Scenario | Strategy |
|----------|----------|
| User input fails a validation rule, show error message | `ensure()` → `onDomainError` |
| Network call fails, show error state | `raise()` → `onDomainError` |
| Silently correct bad input (clamp, trim, default) | `recover { }` with local handling |
| Try something, fall back to a default value | `recover { }.getOrElse { default }` |
| A null that should never be null in production | `orDie()` → `defect` |
| Unexpected exception from a third-party library | caught automatically → `defect` |

---

## Testing error handling

The `anchor-test` DSL captures all error events. Use these assertions in your `verify` block:

| Assertion | What it checks |
|-----------|----------------|
| `assertRaise { error }` | `raise()` was called with this error |
| `assertDomainError { error }` | `onDomainError` handler was invoked |
| `assertOrDie { error }` | `orDie()` was called with this error |
| `assertDefect { throwable }` | `defect` handler was invoked |

### Testing strategy 1 (propagate)

```kotlin
@Test
fun `updateText with empty input raises EmptyInput domain error`() {
    runAnchorTest(RememberAnchorScope::configAnchor) {
        given("initial state") {
            initialState { ConfigState() }
        }

        on("updating with blank text") { updateText("") }

        verify("EmptyInput domain error handled and state reflects error message") {
            assertRaise { ConfigError.EmptyInput }
            assertState { copy(errorMessage = "Text cannot be empty") }
            assertDomainError { ConfigError.EmptyInput }
        }
    }
}
```

`assertRaise` verifies the `raise()` call happened. `assertDomainError` verifies the `onDomainError` handler ran. `assertState` verifies the handler updated the state correctly. All three together confirm the full propagation path.

### Testing strategy 2 (recover locally)

```kotlin
@Test
fun `updateTextClamped silently truncates instead of propagating TooLong`() {
    val longText = "a".repeat(101)
    runAnchorTest(RememberAnchorScope::configAnchor) {
        given("initial state") {
            initialState { ConfigState() }
        }

        on("updating clamped with text over 100 chars") { updateTextClamped(longText) }

        verify("error caught locally, state updated with truncated text") {
            assertRaise { ConfigError.TooLong(maxLength = 100) }
            assertState { copy(text = "a".repeat(100)) }
            // no assertDomainError — the handler was NOT invoked
        }
    }
}
```

The absence of `assertDomainError` is intentional and meaningful: it confirms the error was handled locally and never reached `onDomainError`.
