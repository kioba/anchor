# Spec 022: `anchor()` must dispatch to the anchor its action is typed for, not to whichever `RememberAnchor` is nearest

- **Source issue**: #145 (umbrella), child #132 "Type erasure disaster — unchecked casts can cause runtime ClassCastException". The issue was closed COMPLETED 2025-12-26 by PR #146 (`48a6a3c`). That PR moved the cast; it did not remove it.
- **Severity**: P1. This is a core-contract bug. `anchor()` is documented as "type-safe" with "compile-time type safety" (`AnchorAction.kt:9,15`). With nested anchors, which is the sample app's own layout, a mismatched action crashes the app or is silently misrouted. A workaround exists: hoist the parent's callback outside the nested `RememberAnchor`.
- **Verified at**: origin/master `0bc430c`, 2026-09-29
- **Plan**: [plans/022-anchor-action-type-resolution.md](../022-anchor-action-type-resolution.md). It uses Investigate-then-Act gating, because the change is breaking.
- **Umbrella table**: see [025 §7](025-collectstate-granular-recomposition.md#7-umbrella-145-item-by-item-verification-originmaster-0bc430c)

## 1. Problem statement

`anchor()` fetches the nearest provider and casts blindly.

`anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/AnchorAction.kt:38-48` has the same
pattern at `:81-94`, `:125-138` and `:154-167`:

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

- `LocalAnchor` is `ProvidableCompositionLocal<AnchorScope<*, *>>` (`LocalScope.kt:23-29`). Each `RememberAnchor` overwrites it with its own `ContainerViewModel` (`RememberAnchor.kt:167-171`).
- `A` erases to its bound, so `this as A` only checks `is Anchor` and always succeeds.
- Nesting is the library's own pattern. `MainActivity.kt:19` wraps `MainUi`, which hosts `CounterPage` and `ConfigPage` (`MainUi.kt:45-46`), and each of those opens its own `RememberAnchor`. Inside those pages, `anchor(MainAnchor::selectHome)` compiles, but it resolves to the counter or config anchor.

### Evidence (behavioral)

The tests ran in a disposable worktree at `0bc430c`, with the `desktopTest` harness from plan 025 Step 1. Both use an outer `RememberAnchor<OState>` and an inner `RememberAnchor<IState>`. The action is `anchor(OAnchor::…)`, created inside the inner subtree.

| Outer action body | Observed on `0bc430c` |
|-------------------|------------------------|
| `reduce { copy(n = n + 1) }` | The inner anchor's `defect` received `java.lang.ClassCastException: class …IState cannot be cast to class …OState`. The outer state was unchanged. |
| `post { GoHome }` (touches no typed state) | **Silent misroute**: `outerHandled=0 innerHandled=1 defect=null`. The outer anchor's signal was emitted on the inner anchor's stream. |

Failure routing without a `defect` handler: `safeExecute` → `catchDefects` rethrows (`internal/ErrorHandling.kt:28-33`). The throw goes into `viewModelScope.launch(Dispatchers.Default)` (`ContainerViewModel.kt:36-41`), which has no handler, so the exception is uncaught. That crashes the app on Android and aborts the process on Kotlin/Native (see the repo's Kotlin/Native uncaught-exception note). Outside any `RememberAnchor`, `anchor()` silently does nothing (`LocalScope.kt:25-28`, the default no-op scope for previews).

## 2. Goals / non-goals

**Goals**
- G1: `anchor(X::action)` executes on the nearest enclosing `RememberAnchor` whose anchor matches the action's type, wherever it is called in the subtree.
- G2: A call with no matching `RememberAnchor` fails fast with a message that names the missing state type. Previews are the one exception: they get a decided, documented behavior (open question Q2).
- G3: The documented call form keeps compiling unchanged at all 12 sample call sites: `anchor(SomeAnchor::action)` with arity 0–3.
- G4: The KDoc and docs state the real resolution rule.

**Non-goals**
- `HandleSignal` routing. `LocalSignals` is also nearest-wins (`RememberAnchor.kt:168`), so outer signals are invisible inside a nested subtree. That is a separate question. See Q4.
- `AnchorConsumer`. It exposes the raw nearest `AnchorScope<*, *>`, and its KDoc sample casts manually. Leave it; a typed overload can follow later.
- Compile-time proof that a provider exists. Compose `CompositionLocal`s cannot express that.
- iOS `RememberAnchor`/`AnchorContainer`. That path has no `anchor()`.

## 3. Proposed design (recommended: B1, resolve by ViewState class)

`RememberAnchor` already treats `S::class` as an anchor's identity: its ViewModel key defaults to `S::class.qualifiedName` (`RememberAnchor.kt:155`). The design builds on that.

1. Add `@PublishedApi internal val LocalAnchors: ProvidableCompositionLocal<Map<KClass<*>, AnchorScope<*, *>>>`, which defaults to `emptyMap()`.
2. `RememberAnchor` provides `parent + (S::class to anchorScope)`, so nested anchors accumulate and the nearest one with the same `S` wins. It keeps providing `LocalAnchor` for `AnchorConsumer`.
3. The four `anchor()` overloads become `inline` with a reified `S`, and they take the receiver as `Anchor<R, S, Err>`:

   ```kotlin
   @Composable
   public inline fun <R : Effect, reified S : ViewState, Err : Any> anchor(
     noinline block: suspend Anchor<R, S, Err>.() -> Unit,
   ): () -> Unit {
     val scope = LocalAnchors.current[S::class] ?: missingAnchor(S::class)   // Q2 decides error vs no-op
     return remember(scope, block) {
       {
         scope.execute {
           @Suppress("UNCHECKED_CAST") // safe: the scope was registered under S::class
           (this as Anchor<R, S, Err>).block()
         }
       }
     }
   }
   ```

   The `remember` folds in plan 009, which memoizes the callbacks.

**Prototype result.** A prototype of steps 1-3 was built in a scratch worktree for 0- and 1-arity, under the name `anchorTyped` so it could sit beside `anchor`. It compiled under `allWarningsAsErrors`. It inferred `R`/`S`/`Err` from typealias references (`OAnchor::bump`, `RAnchor::setLabel` with 1 argument). With the outer action called inside a nested inner `RememberAnchor`, the outer state became 1 and no defect was raised. The prototype diff is reproduced in the plan's Phase A.

### Alternatives considered

| Option | Verdict |
|--------|---------|
| **B1 (above)**: resolve by `S::class` | **Recommended.** It fixes the misroute and the crash, and gives a clear error for a missing provider. It is source-compatible for the function-reference form. |
| **B2**: a typed member on the content scope (`AnchorStateScope<R, S, Err>.action(...)`) | Compile-time safe, but only where the receiver is in scope. Deep children would have to thread the scope. Adding `R`/`Err` to `AnchorStateScope` breaks every `AnchorStateScope<MainViewState>.MainUi()` extension (`MainUi.kt:28`). This could complement B1 later; it cannot replace it. |
| **B3**: catch `ClassCastException` at the dispatch boundary and rethrow a clearer error | Rejected. It cannot tell user CCEs from dispatch CCEs. It does nothing for the silent `post {}` misroute, and it still cannot reach the outer anchor. |
| **B4**: docs-only. Correct the "compile-time type safety" KDoc, and document "binds to the nearest `RememberAnchor`; hoist parent callbacks" | Non-breaking, and the minimum if B1 is declined. It leaves the silent misroute in place. |
| Key the lookup by `R`, or by the anchor instance type | `R` is often `EmptyEffect` and shared. Anchors are `Anchor<R, S, Err>` typealiases over the internal `AnchorRuntime`, so no distinct runtime class exists to key on. |

## 4. Behavior and API changes

- **BREAKING (binary)**: the `anchor()` overloads change from non-inline `<A>` to inline `<R, reified S, Err, …>`. Precompiled consumers of `anchor-compose` that call `anchor()` need recompilation, and a reified inline's JVM body is not callable without inlining. Pre-1.0 (0.1.x), this needs a CHANGELOG migration note (plan 027 creates `CHANGELOG.md`).
- **BREAKING (source, narrow)**: explicit type arguments (`anchor<CounterAnchor>(…)`) and lambda literals without an inferable receiver type no longer compile. The repo has none: `git grep -n "anchor<"` is empty and all 12 sample calls are function references. Receivers that are not `Anchor<R, S, Err>` were already disallowed by the old bound.
- **Behavior**: actions of an outer anchor now run on the outer anchor. A missing provider now fails fast (or no-ops in previews, per Q2) instead of silently doing nothing.
- **Unchanged**: `AnchorScope`, `AnchorConsumer`, `LocalAnchor`, `RememberAnchor`'s signature, and iOS.

## 5. Acceptance criteria

1. The regression tests pass after the change and fail before it:
   - (a) an outer `reduce` action under a nested `RememberAnchor` updates the outer state, with no defect;
   - (b) an outer `post {}` action is handled by the outer `HandleSignal`, not the inner one;
   - (c) inner actions still reach the inner anchor.
2. A missing provider produces the Q2-decided behavior, covered by a test.
3. All `features/*` and `androidApp` call sites compile unchanged, and `./gradlew build` exits 0.
4. The KDoc on all four overloads states the resolution rule ("nearest enclosing `RememberAnchor` whose ViewState is `S`"). `docs/compose.md` "Dispatching Actions" gets a nested-anchor note, and `docs/llms-full.txt` is regenerated.
5. If ABI dumps exist by then (plan 029 may introduce them), they are updated deliberately and the break is listed in the CHANGELOG.

## 6. Open questions for the maintainer

1. **GO on B1 (breaking) or B4 (docs-only)?** A middle path is to ship B4 in 0.1.9 and B1 in the next minor.
2. **Missing provider**: error or no-op? `PreviewAnchor` provides no `LocalAnchor` today, so preview code that calls `anchor()` relies on the no-op. The proposal is `error(...)` in `RememberAnchor` trees, plus a no-op entry registered by `PreviewAnchor` for its `S`.
3. **Two live anchors with the same `S`** (sibling or nested, via `customKey`): is "nearest wins" acceptable? That is today's behavior for the nearest provider.
4. Should a follow-up give `HandleSignal` a way to target a specific anchor, so nested subtrees can observe outer signals? It is out of scope here.
