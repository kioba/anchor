# Plan 009: Memoize the callbacks returned by the anchor() composables

> **Triage note (2026-09-29, origin/master `0bc430c`)**: Absorbed by plan 022 if the maintainer picks option B1 there (it rewrites `anchor()` resolution). Decide 022 first.

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 492f7bc..HEAD -- anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- **Priority**: P2
- **Effort**: S
- **Risk**: LOW
- **Depends on**: plans/001-anchor-compose-test-harness.md
- **Category**: perf
- **Planned at**: commit `492f7bc`, 2026-06-11

## Why this matters

All four `anchor()` overloads construct and return a **new lambda on every
recomposition**. Any child composable receiving that callback (e.g.
`Button(onClick = anchor(CounterAnchor::increment))`) sees a changed parameter
each time its parent recomposes, defeating skipping — directly contradicting
the README's "Granular Recomposition" claim. The fix is the standard
`remember` around the returned lambda, keyed on the inputs that can actually
change (`scope` from the CompositionLocal and the `block` reference).

## Current state

- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt:40-48`
  (0-arity overload; the 1-, 2-, 3-arity ones at lines ~92-101, ~131-143,
  ~158-170 have identical structure):

  ```kotlin
  @Composable
  public fun <A> anchor(
    block: suspend A.() -> Unit,
  ): () -> Unit
    where A : Anchor<out Effect, out ViewState, *> {
    val scope = LocalAnchor.current
    return {
      @Suppress("UNCHECKED_CAST")
      scope.execute { (this as A).block() }
    }
  }
  ```

- `LocalAnchor` is provided per-`RememberAnchor` (`LocalScope.kt` /
  `RememberAnchor.kt:167-171`) and holds the `ContainerViewModel`, which is
  stable for the lifetime of the screen.
- Callers conventionally pass function references
  (`anchor(CounterAnchor::increment)`); Kotlin function references have
  value-based `equals`, and lambda arguments are memoized at the call site by
  the Compose compiler (strong-skipping), so `(scope, block)` is a sound
  `remember` key pair.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Module tests | `./gradlew :anchor-compose:desktopTest` | exit 0 |
| Full build | `./gradlew build` | exit 0 |

## Scope

**In scope**:
- `anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt`
- `anchor-compose/src/commonTest/kotlin/dev/kioba/anchor/compose/AnchorActionTest.kt` (create)

**Out of scope**:
- `RememberAnchor.kt`, `LocalScope.kt`, `LocalSignal.kt`.
- README claims (plan 012 owns docs).

## Git workflow

- Branch: `perf/memoize-anchor-callbacks`
- Commit style: gitmoji, e.g. `⚡️ Remember callbacks returned by anchor()`
  (`:zap:` is the gitmoji for performance).
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Step 1: Failing test first

`AnchorActionTest.kt` using the plan-001 harness:

1. `anchor returns the same callback across recompositions` — inside
   `RememberAnchor` content, capture `anchor(TestAnchorAlias::increment)` into
   a list on each composition; drive a recomposition (mutate a
   `mutableStateOf` read by the same composable); assert
   `callbacks[0] === callbacks[1]`. (FAILS today.)
2. `memoized callback still executes the action` — click-through test (reuse
   plan-001's pattern: button + waitUntil state change).

**Verify**: `./gradlew :anchor-compose:desktopTest` → test 1 fails, test 2 passes.

### Step 2: Wrap each overload in remember

Pattern for all four overloads (0–3 arity):

```kotlin
@Composable
public fun <A> anchor(
  block: suspend A.() -> Unit,
): () -> Unit
  where A : Anchor<out Effect, out ViewState, *> {
  val scope = LocalAnchor.current
  return remember(scope, block) {
    {
      @Suppress("UNCHECKED_CAST")
      scope.execute { (this as A).block() }
    }
  }
}
```

Add the `androidx.compose.runtime.remember` import. Keep each overload's
existing KDoc; append one line: "The returned callback is remembered against
the current scope and `block`, so it is referentially stable across
recompositions."

**Verify**: `./gradlew :anchor-compose:desktopTest` → all tests pass.

### Step 3: Full build

**Verify**: `./gradlew build` → exit 0.

## Test plan

Step 1's two tests, plus the existing plan-001 suite as regression. Pattern:
`RememberAnchorTest.kt`.

## Done criteria

- [ ] All four overloads return `remember(scope, block) { ... }`
- [ ] Referential-stability test passes; existing suite green
- [ ] `./gradlew build` exits 0
- [ ] `plans/README.md` status row updated

## STOP conditions

Stop and report back if:

- The stability test still fails after step 2 because `block` itself changes
  identity each recomposition in the harness (would indicate the test passes
  lambdas without compiler memoization — try a top-level function reference
  first; if references are also unstable, report: the key strategy needs an
  advisor decision).
- Plan 001 has not landed (no test harness).

## Maintenance notes

- A lambda with *changing captures* passed to `anchor { ... }` will now create
  a new remembered callback per capture change — correct behavior, but worth a
  docs example discouraging capture-heavy lambdas in favor of function
  references + parameters (the existing KDoc already pushes references).
- Reviewer: confirm no overload was missed (four `fun anchor(` declarations:
  `grep -c "public fun" AnchorAction.kt` → 4, each with `remember`).
