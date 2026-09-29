# anchor-test Module Tests

Comprehensive test coverage for the Anchor BDD test DSL (`runAnchorTest`).

These tests verify the **test infrastructure itself** — that `assertState`, `assertSignal`, `assertRaise`, and all other DSL functions correctly record and match actions. The `anchor` core module has its own unit tests for runtime behavior (`RaiseTest`, `StandaloneRecoverTest`, `ExecuteBoundaryTest`, etc.).

## Test Organization

Tests are ordered from simple to complex across files. Within each file, happy-path tests come first, followed by edge cases, then negative/failure tests.

| File | DSL Area | Tests | Description |
|------|----------|-------|-------------|
| `RunAnchorTestTest.kt` | Entry point | 4 | Minimal DSL lifecycle, factory defaults, basic wiring |
| `ReduceTest.kt` | `assertState` | 7 | State updates, threading across reduces, idempotency, negative cases |
| `SignalTest.kt` | `assertSignal` | 4 | Signal posting, ordering, interleaving with reduces |
| `EventTest.kt` | `assertEvent` | 5 | Event emission, ordering, interleaving with reduces, `init`/`subscriptions` not run |
| `GivenScopeTest.kt` | Setup | 8 | `initialState`, `effectScope`, handler overrides, composition, `given { effect { } }` not run |
| `EffectTest.kt` | `effect { }` | 9 | Effect execution, scope resolution, non-recording behavior, ignored context, `assertEffect` behavior |
| `CancellableTest.kt` | `cancellable` | 4 | Inline execution in tests, mixed actions, multiple blocks, same key does not cancel |
| `WithStateTest.kt` | `withState` | 3 | Read-only state access, post-reduce reads, no-action recording |
| `ErrorHandlingTest.kt` | Error primitives | 17 | `raise`, `recover`, `orDie`, `ensure`, `Recover` extensions |
| `ErrorHandlerBehaviorTest.kt` | Handler effects | 7 | Handlers that reduce/post/emit, pre-raise action preservation |
| `ActionOrderingTest.kt` | Ordering | 5 | Mixed sequences, type mismatches, size mismatches |
| `SequenceTest.kt` | `runAnchorSequenceTest` / `step {}` | 7 | State threading, shared effect, per-step `effect {}`, composable steps, structural errors |

**Total: 80 tests**

## Coverage Map

### DSL Entry Point
- `runAnchorTest` lifecycle: `RunAnchorTestTest`

### GivenScope Functions
| Function | Covered In |
|----------|-----------|
| `initialState { }` | `GivenScopeTest`, `WithStateTest` |
| `effectScope { }` | `GivenScopeTest`, `EffectTest`, `ActionOrderingTest` |
| `onDomainError { }` | `GivenScopeTest`, `ErrorHandlerBehaviorTest` |
| `defect { }` | `GivenScopeTest`, `ErrorHandlerBehaviorTest` |

### VerifyScope Functions
| Function | Covered In |
|----------|-----------|
| `assertState { }` | `ReduceTest`, `RunAnchorTestTest`, `SignalTest`, `EventTest`, all error tests |
| `assertSignal { }` | `SignalTest`, `CancellableTest`, `ErrorHandlerBehaviorTest`, `ActionOrderingTest` |
| `assertEvent { }` | `EventTest`, `ErrorHandlerBehaviorTest`, `ActionOrderingTest` |
| `assertEffect { }` | `EffectTest` (pins the count-check behavior documented on `VerifyScope.assertEffect`) |
| `assertRaise { }` | `ErrorHandlingTest`, `ErrorHandlerBehaviorTest` |
| `assertOrDie { }` | `ErrorHandlingTest`, `ErrorHandlerBehaviorTest` |
| `assertDomainError { }` | `ErrorHandlingTest`, `GivenScopeTest`, `ErrorHandlerBehaviorTest` |
| `assertDefect { }` | `ErrorHandlingTest`, `GivenScopeTest`, `ErrorHandlerBehaviorTest` |

### Anchor Capabilities in Tests
| Capability | Covered In |
|-----------|-----------|
| `reduce { }` | `ReduceTest`, most other files |
| `post { }` | `SignalTest`, `CancellableTest`, `ActionOrderingTest` |
| `emit { }` | `EventTest`, `ActionOrderingTest` |
| `effect { }` | `EffectTest`, `GivenScopeTest`, `ActionOrderingTest` |
| `cancellable { }` | `CancellableTest` |
| `withState { }` | `WithStateTest` |
| `raise()` | `ErrorHandlingTest`, `ErrorHandlerBehaviorTest` |
| `orDie()` | `ErrorHandlingTest`, `ErrorHandlerBehaviorTest` |
| `ensure()` | `ErrorHandlingTest` |
| `recover { }` | `ErrorHandlingTest` |
| `Recover.getOrElse` | `ErrorHandlingTest` |
| `Recover.getOrRaise` | `ErrorHandlingTest` |
| `Recover.getOrNull` | `ErrorHandlingTest` |
| `Recover.getErrorOrNull` | `ErrorHandlingTest` |
| `Recover.fold` | `ErrorHandlingTest` |
| `DefectAnchor.orDie(Recover)` | `ErrorHandlingTest` |

### Sequence DSL

| Function | Covered In |
|----------|-----------|
| `runAnchorSequenceTest` | `SequenceTest`, feature `*SequenceTest` files |
| outer `given { }` (`initialState`, `effectScope`) | `SequenceTest`, feature `*SequenceTest` files |
| `step { }` | `SequenceTest`, feature `*SequenceTest` files |
| `StepGivenScope.effect { }` (per-step mutation of shared scope) | `SequenceTest` |
| `AnchorStepScope` extensions (composable steps) | `SequenceTest`, `CounterSequenceTest`, `MainSequenceTest` |
| `on()` twice without `verify()` → `IllegalStateException` | `SequenceTest` |

### Negative Tests (expected failures)
- Size mismatch (too many/few assertions): `ReduceTest`, `ActionOrderingTest`
- Value mismatch (wrong state/signal/event): `ReduceTest`, `SignalTest`, `EventTest`
- Type mismatch (wrong assertion order): `ActionOrderingTest`

## Test Fixtures

Each file defines its own private fixtures with unique names to avoid Kotlin same-package redeclaration conflicts. Files that don't need error types use `Nothing` (PureAnchor pattern).

| File | Prefix | Has Error Type |
|------|--------|---------------|
| `RunAnchorTestTest.kt` | `Run` | No |
| `ReduceTest.kt` | `Reduce` | No |
| `SignalTest.kt` | `Sig` | No |
| `EventTest.kt` | `Evt` | No |
| `GivenScopeTest.kt` | `Given` | Yes (`GivenErr`) |
| `EffectTest.kt` | `Fx` | No |
| `CancellableTest.kt` | `Cancel` | No |
| `WithStateTest.kt` | `Ws` | No |
| `ErrorHandlingTest.kt` | `Test` | Yes (`TestErr`) |
| `ErrorHandlerBehaviorTest.kt` | `Handler` | Yes (`HandlerErr`) |
| `ActionOrderingTest.kt` | `Ord` | No |

## Known Limitations

### `assertEffect` size mismatch
`assertEffect` adds an `EffectAction` to `expectedActions` but does not consume from `actualActions` during verification. This causes a size mismatch (`expectedActions.size != actualActions.size`) when `assertEffect` is mixed with other assertions. Effects should be verified indirectly through their impact on state.

### `GivenScope.effect { }` is a no-op
`GivenScopeImpl.effects` list is populated by `given { effect { } }` but is never consumed by `AnchorTestScope.assert()`, nor for the outer `given` by `assertSequence()`. Only `effectScope { }` actually replaces the effect scope; only a step's `given { effect { } }` runs. Pinned by `GivenScopeTest`.

### `cancellable` in tests runs inline
`AnchorTestRuntime.cancellable()` executes the block directly without cancellation semantics. Tests cannot verify that a second invocation with the same key cancels the first (`CancellableTest.sameKeyDoesNotCancelInFlightBlock`).

### Other runtime divergences
`AnchorTestRuntime.effect()` ignores the requested context (`EffectTest.effectIgnoresRequestedContext`), `post`/`emit` only record, and the factory's `init`/`subscriptions` never run (`EventTest.initAndSubscriptionsAreNotRun`). The KDoc on `AnchorTestRuntime` and `runAnchorTest` lists them.

## Adding New Tests

1. Identify which DSL area the test covers
2. Add to the appropriate file (or create a new one for a new area)
3. Add a KDoc comment above the test explaining intent and approach
4. Use the file's private fixtures or extend them if needed
5. Follow the pattern: simple first, edge cases next, negative/failure last
6. Use unique fixture prefixes to avoid same-package redeclaration

## Running Tests

```bash
# Run all anchor-test module tests (desktop)
./gradlew :anchor-test:desktopTest

# Run a specific test class
./gradlew :anchor-test:desktopTest --tests "dev.kioba.anchor.test.ReduceTest"

# Run all platforms
./gradlew :anchor-test:allTests
```
