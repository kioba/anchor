# Testing

The `anchor-test` module provides a BDD-style DSL for testing Anchor actions. It lets you verify state changes, signals, events, and effects deterministically — without Compose, ViewModels, or coroutine complexity.

## Installation

```kotlin
dependencies {
    testImplementation("{{ group_id }}:anchor-test:{{ version }}")
}
```

!!! note
    `anchor-test` transitively includes the core `anchor` module and `kotlinx-coroutines-test`.

---

## runAnchorTest

`runAnchorTest` is the entry point for all Anchor tests. Pass it the same anchor factory you use in production, then structure your test with `given`, `on`, and `verify` blocks.

```kotlin
@Test
fun `counter increment updates state`() {
    runAnchorTest(RememberAnchorScope::counterAnchor) {
        given("the screen started") {
            initialState { CounterState(count = -1) }
        }

        on("incrementing the counter", CounterAnchor::increment)

        verify("the state updated with the incremented value") {
            assertState { copy(count = 0) }
            assertSignal { CounterSignal.Increment }
        }
    }
}
```

The test uses a `AnchorTestRuntime` under the hood — a recording implementation that captures every `reduce`, `post`, `emit`, and `effect` call in order, then replays your assertions against the recording.

---

## Given — Setup

The `given` block sets up preconditions for the test.

### `initialState`

Override the default initial state:

```kotlin
given("a counter that already has a value") {
    initialState { CounterState(count = 100) }
}
```

The lambda returns the state to start with. If omitted, the anchor factory's default `initialState` is used.

### `effectScope`

Provide a custom effect scope (useful for injecting test doubles):

```kotlin
given("the API returns a specific user") {
    effectScope { MyEffect(api = FakeApi()) }
}
```

---

## On — Action

The `on` block specifies which action to execute. You can pass a function reference or a lambda:

```kotlin
// Function reference
on("incrementing the counter", CounterAnchor::increment)

// Lambda (for actions with parameters)
on("updating the text") {
    updateText("Hello")
}
```

---

## Verify — Assertions

The `verify` block asserts the outcomes of the action. Assertions must be declared **in the same order** the action produces them.

### `assertState`

Verifies a state reduction occurred. The lambda receives the previous state and returns the expected new state:

```kotlin
verify("the count increased") {
    assertState { copy(count = count + 1) }
}
```

### `assertSignal`

Verifies a signal was posted:

```kotlin
verify("an increment signal was sent") {
    assertSignal { CounterSignal.Increment }
}
```

### `assertEvent`

Verifies an internal event was emitted:

```kotlin
verify("a cancel event was emitted") {
    assertEvent { MainEvent.Cancel }
}
```

### `assertEffect`

Verifies an effect block was executed:

```kotlin
verify("the API was called") {
    assertEffect { api.fetchData() }
}
```

### Ordering

Assertions are matched against recorded actions **in order**. If an action calls `reduce`, then `post`, your verify block must call `assertState` before `assertSignal`:

```kotlin
// Action
suspend fun CounterAnchor.increment() {
    reduce { copy(count = count + 1) }    // 1st
    post { CounterSignal.Increment }       // 2nd
}

// Test
verify("state updated and signal posted") {
    assertState { copy(count = 0) }        // matches 1st
    assertSignal { CounterSignal.Increment } // matches 2nd
}
```

---

## Examples

### Basic state and signal test

```kotlin
@Test
fun `counter increment updates state`() {
    runAnchorTest(RememberAnchorScope::counterAnchor) {
        given("the screen started") {
            initialState { CounterState(count = -1) }
        }

        on("incrementing the counter", CounterAnchor::increment)

        verify("the state updated with the incremented value") {
            assertState { copy(count = 0) }
            assertSignal { CounterSignal.Increment }
        }
    }
}
```

### Testing events

```kotlin
@Test
fun `clear cancels the counting`() {
    runAnchorTest(RememberAnchorScope::mainAnchor) {
        given("the initial state started to count up") {
            initialState { mainViewState().copy(hundreds = 100, iterationCounter = "100") }
        }

        on("clearing", MainAnchor::clear)

        verify("cancel event emitted and state cleared") {
            assertEvent { MainEvent.Cancel }
            assertState { copy(details = "cleared", iterationCounter = null) }
        }
    }
}
```

### Providing a custom effect scope

```kotlin
@Test
fun `refresh triggers the subscription`() {
    runAnchorTest(RememberAnchorScope::mainAnchor) {
        given("the screen is ready") {
            initialState { mainViewState() }
            effectScope { MainEffect() }
        }

        on("refreshing", MainAnchor::refresh)

        verify("refresh event emitted") {
            assertEvent { MainEvent.Refresh }
        }
    }
}
```

### Skipping the given block

If you don't need custom setup, you can omit the `given` block entirely. The test will use the anchor factory's default state and effect scope:

```kotlin
@Test
fun `sayHi sets the details`() {
    runAnchorTest(RememberAnchorScope::mainAnchor) {
        on("saying hi", MainAnchor::sayHi)

        verify("the details are set") {
            assertState { copy(details = "Hello Android!") }
        }
    }
}
```
