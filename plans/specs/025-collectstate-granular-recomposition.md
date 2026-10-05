# Spec 025: `collectState` must recompose only on selected-slice changes and must track selector changes

- **Source issue**: #145 (umbrella), child #143 "State updates trigger unnecessary full recompositions" (closed COMPLETED 2026-01-23 by PR #152 / `1ee66f2`)
- **Severity**: P1. The stale-value half is a correctness bug users hit in normal use, and the recomposition half breaks a documented performance contract. Both have a workaround: read `state` directly.
- **Verified at**: origin/master `0bc430c`, 2026-09-29
- **Independent confirmation**: the #140 (documentation) verifier hit the same defect separately. Its headless Compose probes show caller compositions going 2→3→4 on unselected-field changes and a selector flip staying stale (`afterFlip=0`), and the same `derivedStateOf` prototype fixes both. Its duplicate spec/plan pair was merged into this one during triage (2026-09-29).
- **Plan**: [plans/025-collectstate-granular-recomposition.md](../025-collectstate-granular-recomposition.md)
- **Sibling specs from the same umbrella**: [022](022-anchor-action-type-resolution.md) (`anchor()` type resolution), [026](026-cancellable-key-isolation.md) (`cancellable` key isolation)

## 1. Problem statement

PR #152 closed #143 by adding `AnchorStateScope.collectState(selector)`. The KDoc and docs promise
granular recomposition. The implementation delivers neither granular recomposition nor correct
re-selection.

`anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/RememberAnchor.kt:65-72`:

```kotlin
  @Composable
  override fun <T> collectState(
    selector: (S) -> T,
  ): T {
    val updatedSelector = rememberUpdatedState(selector)
    val state by stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    return remember(state, updatedSelector) { updatedSelector.value(state) }
  }
```

Defect 1 is that every state change recomposes the caller. `state` is a delegated read of the whole
`State<S>`, and it runs inside `collectState`. A composable with a non-`Unit` return type is not
restartable, so the read invalidates the caller's restart scope on every emission, whatever
`selector` returns.

Defect 2 is a stale value when the selector changes but the state does not. The `remember` keys are
`state` and `updatedSelector`. `updatedSelector` is the `State` holder returned by
`rememberUpdatedState`, and it is the same object on every composition. A selector that captures a
composition value (`collectState { it.items[index] }`) therefore keeps returning the old result until
some unrelated anchor state change busts the cache.

Contract being violated:
- `RememberAnchor.kt:41`: "Recomposes only when the selected value (returned by the [selector]) changes."
- `RememberAnchor.kt:32-33`: "`state` … will cause the calling Composable to recompose whenever any part of the state changes. For more granular observation, use [collectState]."
- `docs/compose.md:69-81`: "`collectState` applies a selector function and only triggers recomposition when the selected value changes."
- `docs/api.md:54`, `docs/index.md:18`, `README.md:19` ("Granular Recomposition").

### Evidence (behavioral)

The tests were run in a disposable worktree at `0bc430c`. The harness is described in the plan.
The command was `./gradlew :anchor-compose:desktopTest --tests '*Issue145ComposeTest*'`.

| Test | master `0bc430c` | with proposed fix |
|------|------------------|-------------------|
| Caller of `collectState { it.count }` sees 3 `label`-only updates | **FAIL**: probe recomposed 3× (`baseline=1 afterUnrelated=4`) | PASS (`baseline=1 afterUnrelated=1`; after a `count` change it is 2) |
| `collectState { it.count + offset }` sees `offset` change 0→10, state unchanged | **FAIL**: `expected:<10> but was:<0>` | PASS |
| Positive control: a `count` change is observed | PASS | PASS |

No existing test covers this. `anchor-compose` has no test source set, and plan 001's test 4
explicitly puts "recomposition counting" out of scope.

## 2. Goals / non-goals

**Goals**
- G1: The caller of `collectState(selector)` is invalidated only when `selector(state)` changes under structural equality.
- G2: A new selector instance, such as one that captures changed composition values, is re-applied on the same recomposition.
- G3: Signature and semantics of the `state` property stay unchanged. It still recomposes on any change.
- G4: Regression tests that fail on `0bc430c` and pass after the fix.

**Non-goals**
- Changing `RememberAnchor`'s content-lambda recomposition model. Reading `state` in the content lambda still recomposes it.
- A new public API such as `deriveState`/`project` (#143's options 1-3). The existing `collectState` becomes correct instead.
- iOS/Swift observation. `collectState` is Compose-only.
- The Compose test harness in general. Plan 001 owns that. This spec needs only a minimal `desktopTest` set-up.

## 3. Proposed design

Back the selection with `derivedStateOf`:

```kotlin
  @Composable
  override fun <T> collectState(
    selector: (S) -> T,
  ): T {
    val updatedSelector = rememberUpdatedState(selector)
    val state = stateFlow.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    val selected = remember(state) { derivedStateOf { updatedSelector.value(state.value) } }
    return selected.value
  }
```

- The composition no longer reads the whole `state.value`. Only `derivedStateOf`'s calculation reads it. The caller reads `selected.value`, which `derivedStateOf` invalidates only when the result changes under its default `structuralEqualityPolicy()`. This meets G1.
- `updatedSelector.value` is read inside the derived calculation. A new selector instance therefore invalidates the derived state and is recomputed on the read in the same composition. This meets G2.
- `remember(state)` is keyed on the `State` object from `produceState`, which is stable for the call site's lifetime.

This exact code was prototyped and passed all three tests in the table above.

### Alternatives considered

| Alternative | Why rejected |
|-------------|--------------|
| `remember(stateFlow) { stateFlow.map(selector).distinctUntilChanged() }.collectAsStateWithLifecycle(selector(stateFlow.value))` | This captures the first selector forever, which is defect 2 again. Keying on `selector` instead re-subscribes on every recomposition that has an unstable lambda. |
| Keep the current code and key `remember` on `selector` instead of `updatedSelector` | This fixes defect 2 only. The full-state read still recomposes the caller on every change (defect 1). |
| Make `collectState` `inline` | This is not possible for an interface member. The read would still happen in the caller. |
| Docs-only: tell users to wrap in `derivedStateOf` themselves | The API's whole purpose is this behavior, and README/docs advertise it as a feature. |

## 4. Behavior and API changes

- **Non-breaking.** No signature change. `AnchorStateScope` is a public interface, and user implementations, if any, are unaffected.
- Behavior change: callers recompose less often. The selector result now updates when the selector changes without a state change. That result was stale before, so the change is a bug fix.
- Subtle change: the selector's result is compared with `equals`. A selector that returns a fresh, structurally equal object, such as a new `List` with the same content, no longer triggers recomposition. That is what the docs promise.

## 5. Acceptance criteria

1. The new `desktopTest` suite in `anchor-compose` contains the three tests from §1. Two of them fail on unmodified `0bc430c`, and all three pass after the change.
2. `RememberAnchor.kt:38-55` KDoc (or its new home, see plan 029 B5) states that only selected-value changes recompose the caller, that selector changes are honored, and that equality is structural.
3. `./gradlew :anchor-compose:desktopTest` and `./gradlew build` exit 0.
4. No change to `docs/`. The docs already describe the fixed behavior, so there is no `llms-full.txt` regeneration.

## 6. Open questions for the maintainer

1. Should the harness land here as `desktopTest`, or wait for plan 001's `commonTest` harness? The plan is self-contained with `desktopTest`, and plan 001 needs refreshing anyway: its `runComposeUiTest` is deprecated in CMP 1.11.1 and fails `-Werror`, and the desktop test classpath lacks a Main dispatcher.
2. Is structural equality the right default? An overload that takes a `SnapshotMutationPolicy` could be added later. It is not proposed here.

## 7. Umbrella #145: item-by-item verification (origin/master `0bc430c`)

| # | Umbrella claim | Verdict | Evidence | Carried by |
|---|----------------|---------|----------|------------|
| #129 | `cancellable()` race runs several jobs per key | **FIXED for the reported race** (PR #147, `8f361aa`). **Residual STILL REAL**: if a queued caller is cancelled while its predecessor ignores cancellation (a blocking `effect {}`), the next call overlaps the predecessor. | `AnchorRuntime.kt:177-220` puts the map swap and cancel under `jobsMutex`. A new test with 200 concurrent same-key calls on `Dispatchers.Default` saw max concurrency 1, and the existing Cancellable/JobIdentity suites pass (20/20). The residual test got `expected:<1> but was:<2>`. | **026** (P2) |
| #130 | `jobs` map leaks | **FIXED for the reported scenario**: 1,000 unique keys left 0 entries. **Residual STILL REAL**: an entry leaks when a job is cancelled from outside during mutex contention, and one key's slow cancellation stalls every other key for ~350 ms (blocking `effect {}`). | New tests: `stale entries left in jobs map: [k1]`, `unrelated key blocked for 353ms` | **026** (P2) |
| #131 | Signals re-fire on every recomposition | **FIXED** (PR #152, `1ee66f2`) | `LocalSignal.kt:44-51` keys `LaunchedEffect` on the per-post `SignalProvider` instance. A new test recomposed 5×: 1 delivery. | Remaining signal defects: conflation and cancelled handler go to **plan 002**; loss before a collector attaches goes to **#266** / plan 024 (supersedes 017) |
| #132 | Unchecked casts in `anchor()` cause `ClassCastException` | **STILL REAL** (PR #146 moved the cast, did not remove it) | `AnchorAction.kt:45-46,91-92,135-136,164-165` `(this as A)` against `LocalAnchor: AnchorScope<*, *>` (`LocalScope.kt:23-29`). A new test calling an outer anchor's action under a nested `RememberAnchor` (the sample app nests: `MainActivity.kt:19` → `MainUi.kt:45-46`) got `ClassCastException: IState cannot be cast to OState`. | **022** (P1) |
| #133 | Marker interfaces add no value | COVERED ELSEWHERE | Closed COMPLETED 2026-06-02 with no design note | **plan 020** |
| #134 | `SubscriptionScope`/`SignalScope` pointless | NOT A DEFECT (maintainer decision) | Still at `Anchor.kt:236-237,262-263`. Closed NOT_PLANNED 2026-05-06: "scopes still provide useful extension points". | — |
| #135 | Inconsistent `suspend` | NOT A DEFECT (maintainer decision) | Closed NOT_PLANNED 2026-05-06 ("required due to iOS implementation"). `reduce` is already non-suspend (`Anchor.kt:172`). | — |
| #136 | `withState` is `.run()` | NOT A DEFECT (kept de facto). **Tracker note**: closed COMPLETED 2026-01-23 citing `c835d3c`, which exists only on the parked branch `origin/fix-signal-handling-801299363541751028` and never reached master. | `Anchor.kt:149-152` still ships it. The maintainer later added `WithStateTest.kt` (3 tests, `2e9d494`, #195, 2026-05-03). | Spec 024 delivers the parked-branch verdict: "delete it; nothing to mine". Removing `withState` would be a new deprecation decision, not a defect fix. |
| #137 | `CounterAnchorType` dead code | **FIXED** (`1ee66f2`) | `git grep -w CounterAnchorType origin/master` returns empty | — |
| #138 | Effect scope half-baked | NOT A DEFECT | Effect is the dependency container (`docs/concepts.md:10,32`, `features/*/data/*Effects`). Closed 2026-05-06. | Doc depth goes to **#140** |
| #139 | Test DSL limits | **FIXED** (PR #214 `eacb4c5` `runAnchorSequenceTest`; `runTest` virtual time `AnchorTest.kt:18,28`). `assertState { copy() }` was kept by design (PR #214 notes). | — | Divergence docs go to **plan 006** |
| #140 | Documentation lacking | COVERED ELSEWHERE | open | **#140**, plans 012/013 |
| #141 | Naming conventions | NOT A DEFECT (maintainer decision) | Closed NOT_PLANNED 2026-05-06 | — |
| #142 | File organization | COVERED ELSEWHERE | open | **#142**, plan 029 |
| #143 | Unnecessary recompositions | **STILL REAL**: `collectState` added by PR #152 recomposes on every change and returns stale values | §1 above | **this spec (025)** |
| #144 | Signal loss, `replay = 0` | COVERED ELSEWHERE (closed without a real fix) | `AnchorRuntime.kt:59-60` | **#266**, plan 024 |

**Can #145 be closed?** Yes, once 025/b/c are tracked on their own, as new issues or by
reopening #143 and #132 with this evidence. Every other item is fixed, declined by the maintainer, or
carried by #140, #142, #266 or plans 002, 006 and 020.
