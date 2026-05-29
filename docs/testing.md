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

Single-action tests cover one `on/verify` pair in isolation. Use `runAnchorSequenceTest` when you need to verify that several actions compose correctly over time — for example, that a second action starts from the state the first action produced, not from the original initial state.

### runAnchorSequenceTest

`runAnchorSequenceTest` is a dedicated entry point, separate from `runAnchorTest`. The body is a list of `step {}` blocks, optionally preceded by an outer `given {}` that seeds the starting state and shared effect scope:

```kotlin
@Test
fun `increment twice threads state`() =
    runAnchorSequenceTest(RememberAnchorScope::counterAnchor) {
        given("start at 0") { initialState { CounterState(count = 0) } }

        step("first increment") {
            on("increment") { increment() }
            verify("count incremented") {
                assertState { copy(count = count + 1) }   // 0 → 1
            }
        }
        step("second increment") {
            on("increment") { increment() }
            verify("count incremented") {
                assertState { copy(count = count + 1) }   // 1 → 2
            }
        }
    }
```

Every block (`given`, `step`, `on`, `verify`) takes a description string. Descriptions are not yet used by the runner — they document intent inline and are reserved for future test reporting.

### step { }

`step {}` runs a single `on/verify` pair as one unit. Each step's final state becomes the next step's starting state.

!!! note
    `assertState`'s lambda receives the **current step's input state** as its receiver, not the outer initial state. Use relative expressions like `copy(count = count + 1)` rather than hardcoded absolute values; the assertion will always be evaluated against the correct intermediate state.

A step's `given {}` block is restricted to `effect {}`, `onDomainError {}`, and `defect {}` — `initialState {}` and `effectScope {}` are intentionally not exposed on `StepGivenScope`, so calling them inside a step is a **compile error**, not a silent no-op. State and effect lifetime belong to the outer `given {}`.

### State threading

The outer `given {}` is the single source of truth for the sequence's starting state. Subsequent steps inherit the state produced by the prior step:

```kotlin
given("start at 5") { initialState { CounterState(count = 5) } }

step("increment once") {
    on("increment") { increment() }
    verify("count goes 5 → 6") {
        assertState { copy(count = count + 1) }
    }
}
```

### Shared effect scope

`effectScope {}` on the outer `given {}` creates a **single** Effect instance that is shared across every step in the sequence. All steps see the same object:

```kotlin
runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
    given("shared effect scope") { effectScope { SeqEffect(fetchResult = "shared") } }

    step("first fetch") {
        on("fetch and set") { fetchAndSet() }
        verify("value from shared effect") {
            assertState { copy(value = "shared") }
        }
    }
    step("second fetch") {
        on("fetch and set") { fetchAndSet() }
        verify("same effect scope reused") {
            assertState { copy(value = "shared") }
        }
    }
}
```

### Per-step effect configuration

A step can mutate the shared Effect instance via `effect {}` inside its `given {}` block. The object is not replaced — its mutable fields are reconfigured for that step's action:

```kotlin
runAnchorSequenceTest(RememberAnchorScope::seqAnchor) {
    given("base effect") { effectScope { SeqEffect() } }

    step("returns step1") {
        given("configure step1 result") { effect { fetchResult = "step1" } }
        on("fetch and set") { fetchAndSet() }
        verify("value is step1") {
            assertState { copy(value = "step1") }
        }
    }
    step("returns step2") {
        given("configure step2 result") { effect { fetchResult = "step2" } }
        on("fetch and set") { fetchAndSet() }
        verify("value is step2") {
            assertState { copy(value = "step2") }
        }
    }
}
```

### Composable step extensions

Repeated step patterns extract cleanly as extension functions on `AnchorStepScope<R, S, Err>`. The extension can only be invoked inside a `step {}` block — it cannot be reused as a standalone `runAnchorTest`, because its receiver type does not match `AnchorTestScope`.

```kotlin
private fun AnchorStepScope<CounterEffect, CounterState, Nothing>.incrementStep() {
    on("increment") { increment() }
    verify("count incremented") {
        assertState { copy(count = count.inc()) }
        assertSignal { CounterSignal.Increment }
    }
}

private fun AnchorStepScope<CounterEffect, CounterState, Nothing>.decrementStep() {
    on("decrement") { decrement() }
    verify("count decremented") {
        assertState { copy(count = count.dec()) }
        assertSignal { CounterSignal.Decrement }
    }
}

@Test
fun `increment twice then decrement`() =
    runAnchorSequenceTest(RememberAnchorScope::counterAnchor) {
        given("start at 0") { initialState { CounterState(count = 0) } }

        step { incrementStep() }   // 0 → 1
        step { incrementStep() }   // 1 → 2
        step { decrementStep() }   // 2 → 1
    }
```

Because state threads automatically, the same `incrementStep()` works at any starting count.

### Programming errors

Calling `on()` twice inside the same step without an intervening `verify()` is a structural mistake. The DSL throws `IllegalStateException` immediately so the failure surfaces at the call site rather than as a confusing downstream assertion:

```kotlin
step("on called twice throws") {
    on("first") { increment() }
    on("second without verify") { increment() }  // throws IllegalStateException
    verify("unreachable") { assertState { copy(count = count + 1) } }
}
```

---

## Sequence examples

### Three increments

```kotlin
@Test
fun `three increments thread state`() =
    runAnchorSequenceTest(RememberAnchorScope::counterAnchor) {
        given("start at 0") { initialState { CounterState(count = 0) } }

        step("increment 1") {
            on("increment") { increment() }
            verify("count incremented") {
                assertState { copy(count = count.inc()) }   // 0 → 1
                assertSignal { CounterSignal.Increment }
            }
        }
        step("increment 2") {
            on("increment") { increment() }
            verify("count incremented") {
                assertState { copy(count = count.inc()) }   // 1 → 2
                assertSignal { CounterSignal.Increment }
            }
        }
        step("increment 3") {
            on("increment") { increment() }
            verify("count incremented") {
                assertState { copy(count = count.inc()) }   // 2 → 3
                assertSignal { CounterSignal.Increment }
            }
        }
    }
```

### Error then dismiss

State threads between steps, so the dismiss step's receiver already has `errorDialog` set by the previous step:

```kotlin
@Test
fun `local error then dismiss`() =
    runAnchorSequenceTest(RememberAnchorScope::mainAnchor) {
        given("initial main view state") { initialState { mainViewState() } }

        step("trigger local error") {
            on("trigger local error") { triggerLocalError() }
            verify("error dialog set") {
                assertState { copy(errorDialog = "A locally caught error occurred.") }
            }
        }
        step("dismiss error dialog") {
            on("dismiss error dialog") { dismissErrorDialog() }
            verify("error dialog cleared") {
                assertState { copy(errorDialog = null) }
            }
        }
    }
```

### Navigation with composable steps

Tab-selection steps extract as `AnchorStepScope` extensions and compose in sequence. Each step's `assertState` lambda reuses the same reducer function from production code:

```kotlin
private fun AnchorStepScope<MainEffect, MainViewState, Nothing>.selectCounterStep() {
    on("select counter") { selectCounter() }
    verify("counter tab selected") { assertState { updateCounterSelected() } }
}

private fun AnchorStepScope<MainEffect, MainViewState, Nothing>.selectHomeStep() {
    on("select home") { selectHome() }
    verify("home tab selected") { assertState { updateHomeSelected() } }
}

@Test
fun `tab navigation threads selected tab`() =
    runAnchorSequenceTest(RememberAnchorScope::mainAnchor) {
        given("initial main view state") { initialState { mainViewState() } }

        step { selectCounterStep() }
        step { selectHomeStep() }
    }
```
