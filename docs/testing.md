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

---

## Sequence Testing

Single-action tests cover one `on/verify` pair in isolation. Use `sequence {}` when you need to verify that several actions compose correctly over time — for example, that a second action starts from the state the first action produced, not from the original initial state.

### sequence { }

Wrap your steps in `sequence {}`. The outer `given` block sets the starting state; each step's final state automatically becomes the next step's starting state.

```kotlin
@Test
fun `increment twice threads state`() = runAnchorTest(RememberAnchorScope::counterAnchor) {
    given { initialState { CounterState(count = 0) } }

    sequence {
        step("first increment") {
            on { increment() }
            verify { assertState { copy(count = count + 1) } }   // 0 → 1
        }
        step("second increment") {
            on { increment() }
            verify { assertState { copy(count = count + 1) } }   // 1 → 2
        }
    }
}
```

### step { }

`step {}` groups a single `on/verify` pair and gives it an optional description. It has no effect on execution — it is purely a visual delimiter that makes each step's boundary explicit at a glance.

!!! note
    `assertState`'s lambda receives the **current step's input state** as its receiver, not the outer initial state. Use relative expressions like `copy(count = count + 1)` rather than hardcoded absolute values; the assertion will always be evaluated against the correct intermediate state.

### State threading

The outer `given` block is the single source of truth for the sequence's starting state. Step-level `initialState { }` calls are silently suppressed — only `effectScope` overrides are applied per step:

```kotlin
given { initialState { CounterState(count = 5) } }

sequence {
    step {
        given {
            initialState { CounterState(count = 0) }  // ignored — outer given wins, count stays 5
        }
        on { increment() }
        verify { assertState { copy(count = count + 1) } }  // 5 → 6
    }
}
```

### Per-step effect scope

Each step can supply its own `effectScope` override inside a `given {}` block. The override applies only to that step; subsequent steps use the base effect scope unless they override it too:

```kotlin
sequence {
    step("fetch from source A") {
        given { effectScope { ApiEffect(api = FakeApi(response = "A")) } }
        on { fetchAndStore() }
        verify { assertState { copy(value = "A") } }
    }
    step("fetch from source B") {
        given { effectScope { ApiEffect(api = FakeApi(response = "B")) } }
        on { fetchAndStore() }
        verify { assertState { copy(value = "B") } }
    }
}
```

### Composable step extensions

A repeated step pattern can be extracted into an extension function on `AnchorTestScope`. The same function works in two contexts:

- **Standalone** (outside `sequence {}`): its `initialState { }` is live and drives the test.
- **Inside `sequence {}`**: its `initialState { }` is suppressed; state threads from the prior step.

```kotlin
private fun AnchorTestScope<CounterEffect, CounterState, Nothing>.incrementStep() {
    given { initialState { CounterState() } }   // live standalone, suppressed in sequence
    on { increment() }
    verify {
        assertState { copy(count = count.inc()) }
        assertSignal { CounterSignal.Increment }
    }
}

private fun AnchorTestScope<CounterEffect, CounterState, Nothing>.decrementStep() {
    given { initialState { CounterState() } }
    on { decrement() }
    verify {
        assertState { copy(count = count.dec()) }
        assertSignal { CounterSignal.Decrement }
    }
}

@Test
fun `increment twice then decrement`() = runAnchorTest(RememberAnchorScope::counterAnchor) {
    given { initialState { CounterState(count = 0) } }

    sequence {
        step { incrementStep() }   // 0 → 1
        step { incrementStep() }   // 1 → 2
        step { decrementStep() }   // 2 → 1
    }
}

// The same extension also runs standalone, using its own initialState:
@Test
fun `increment step standalone`() = runAnchorTest(RememberAnchorScope::counterAnchor) {
    incrementStep()   // CounterState() → count 0 → 1
}
```

---

## Sequence examples

### Multi-step state

```kotlin
@Test
fun `three increments thread state`() = runAnchorTest(RememberAnchorScope::counterAnchor) {
    given { initialState { CounterState(count = 0) } }

    sequence {
        step("increment 1") {
            on { increment() }
            verify {
                assertState { copy(count = count.inc()) }   // 0 → 1
                assertSignal { CounterSignal.Increment }
            }
        }
        step("increment 2") {
            on { increment() }
            verify {
                assertState { copy(count = count.inc()) }   // 1 → 2
                assertSignal { CounterSignal.Increment }
            }
        }
        step("increment 3") {
            on { increment() }
            verify {
                assertState { copy(count = count.inc()) }   // 2 → 3
                assertSignal { CounterSignal.Increment }
            }
        }
    }
}
```

### Error then dismiss

State threads between steps, so the dismiss step's receiver already has `errorDialog` set by the previous step:

```kotlin
@Test
fun `local error then dismiss`() = runAnchorTest(RememberAnchorScope::mainAnchor) {
    given { initialState { mainViewState() } }

    sequence {
        step("trigger local error") {
            on { triggerLocalError() }
            verify {
                assertState { copy(errorDialog = "A locally caught error occurred.") }
            }
        }
        step("dismiss error dialog") {
            on { dismissErrorDialog() }
            verify {
                assertState { copy(errorDialog = null) }
            }
        }
    }
}
```

### Navigation with composable steps

Tab-selection steps are extracted as extensions and composed in sequence. Each step's `assertState` lambda uses the same reducer function from production code:

```kotlin
private fun AnchorTestScope<MainEffect, MainViewState, Nothing>.selectCounterStep() {
    on { selectCounter() }
    verify { assertState { updateCounterSelected() } }
}

private fun AnchorTestScope<MainEffect, MainViewState, Nothing>.selectHomeStep() {
    on { selectHome() }
    verify { assertState { updateHomeSelected() } }
}

@Test
fun `tab navigation threads selected tab`() = runAnchorTest(RememberAnchorScope::mainAnchor) {
    given { initialState { mainViewState() } }

    sequence {
        step { selectCounterStep() }
        step { selectHomeStep() }
    }
}
```
