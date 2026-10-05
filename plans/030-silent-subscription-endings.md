# Plan 030: Route foreign cancellations and unhandled `raise` to `defect`, invoke a throwing handler once, and keep subscriptions alive through `init`

> **Executor instructions**: Follow this plan step by step. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md` — unless a reviewer dispatched you and told you they
> maintain the index.
>
> **Drift check (run first)**: `git diff --stat 0048d11..HEAD -- anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt anchor/src/commonTest/ docs/errors.md`
> (`0048d11` = `0048d1173e712e4d6e96bed67741faab22ccf467`, the tip of
> `origin/fix/004-init-event-delivery` / PR #273 when this plan was written.)
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition. Plan 003's own changes are **expected
> drift**:
> - `SubscriptionDsl.kt` gains `anchorErrors()` plus KDoc on `.anchor {}` and `connect`;
> - `commonTest/` gains `SubscriptionIsolationTest.kt` and `AnchorErrorsTest.kt`.
>
> Continue if these still match the excerpts: the *body* of `.anchor {}`,
> `handlers()`, the launch loop in `subscribe()`, `safeExecute`/`catchDefects`,
> and `ContainerViewModel.init`.

## Status

- **Priority**: P1
- **Effort**: M. There are four small production changes, about 16 new tests, and some test and doc updates.
- **Risk**: MED. It changes which errors reach `defect` on every path (actions, `init` and subscriptions). The API is unchanged.
- **Depends on**: **plan 003's branch `fix/003-subscription-restart`**, which is stacked on PR #273 (`fix/004-init-event-delivery`) and therefore on #272. Both 003 and 030 change `SubscriptionDsl`/`safeExecute` routing, and 030 flips three tests that 003 pins. If 003 has become a PR by then, branch from its head. If it has merged, branch from `master`.
- **Category**: bug
- **Planned at**: `0048d11` (`origin/fix/004-init-event-delivery`), 2026-09-29. The test pins come from plan 003's `e8f6126`.
- **Spec**: [`plans/specs/030-silent-subscription-endings.md`](specs/030-silent-subscription-endings.md)
- **Issue**: none. It was found by plan 003's characterization tests; recommend a dedicated issue.
- **Gating**: Investigate-then-Act. Phase A writes the tests and a prototype and commits no production code. Then comes a **mandatory STOP for maintainer GO** on spec Q1–Q4 and Q6 (Q5 is conditional). Phase B implements.

## Why this matters

Error routing assumes every `CancellationException` is the coroutine's own cancellation, and that a domain error only ever meets `onDomainError`. Four defects follow (spec §1.1). All of them were confirmed by failing tests on desktop and iOS, at `0048d11` and on master `0bc430c`, so they predate the in-flight PRs.

- **A1**: a `withTimeout` expiry, or any other cancellation that does not come from the scope, never reaches `defect`. Inside `.anchor {}` it silently ends the listener for the anchor's lifetime. `docs/errors.md:130` promises that `defect` runs "for any uncaught `Throwable` from your action".
- **A2**: a `raise` with no `onDomainError` never reaches `defect`, even when `defect` is configured. It is silent on every path, and inside `.anchor {}` it ends the listener.
- **A3**: a `defect` handler that throws inside `.anchor {}` is invoked a second time with its own exception.
- **A4**: when `init` ends with a cancellation that `safeExecute` rethrows (A1 or A2), **every** `connect` listener is cancelled.

The execute path shares `safeExecute`, so A1 and A2 are silent there too; only the blast radius differs. The fixes are small and confined to `ErrorHandling.kt`, `.anchor {}`, `handlers()`/`subscribe()` and `ContainerViewModel.init`. A throwaway prototype of all four passed every test except the expected flips (spec §3.7). Two of the fixes change what `defect` sees, so the maintainer decides first.

## Current state

All excerpts are at `0048d11`. `ErrorHandling.kt` and `SubscriptionDsl.kt` are identical on master `0bc430c`.

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt:23-47`:

  ```kotlin
  public suspend inline fun <R, S, Err> catchDefects(
    anchor: Anchor<R, S, Err>,
    noinline defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
    block: () -> Unit,
  ) where R : Effect, S : ViewState, Err : Any {
    try {
      block()
    } catch (e: Throwable) {
      if (!e.isNonFatal()) throw e
      defect?.invoke(anchor, e) ?: throw e
    }
  }

  public suspend inline fun <R, S, Err> safeExecute(
    anchor: Anchor<R, S, Err>,
    noinline onDomainError: (suspend ErrorScope<R, S>.(Err) -> Unit)?,
    noinline defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
    block: () -> Unit,
  ) where R : Effect, S : ViewState, Err : Any {
    catchDefects(anchor, defect) {
      catchDomainError(anchor, onDomainError) {
        block()
      }
    }
  }
  ```

  `catchDomainError` (`:9-21`) rethrows the `RaisedException` when `onDomainError` is null. `isNonFatal()` is false for every `CancellationException`: see `iosMain/.../internal/NonFatal.kt:6-7` (`this !is CancellationException`) and the JVM/Android actuals.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/AnchorExceptions.kt:16-19` and `:29-31`: `RaisedException` extends `CancellationException`, and `DomainDefectException` extends `RuntimeException`.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt:46-53`:

  ```kotlin
  public fun <I> Flow<I>.anchor(
    action: suspend Anchor<R, S, Err>.(I) -> Unit,
  ): Flow<I> =
    onEach { value ->
      safeExecute(anchor, onDomainError, defect) {
        anchor.action(value)
      }
    }
  ```

  The KDoc (`:38-40`) says: "If no handler is configured, the exception propagates and cancels the subscription."
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:133-148`, the chain-level `catch` that reroutes what escapes `.anchor {}` (A3):

  ```kotlin
  private suspend fun <T : Event> SharedFlow<T>.handlers(): List<Flow<*>> =
    SubscriptionsScope<R, S, Err>(
      chain = this,
      anchor = this@AnchorRuntime,
      effect = effect,
      onDomainError = onDomainError,
      defect = defect,
    ).also { scope -> subscriptions?.invoke(scope) }
      .flows
      .map { flow ->
        flow.catch { e ->
          safeExecute(this@AnchorRuntime, onDomainError, defect) {
            throw e
          }
        }
      }
  ```

- `AnchorRuntime.kt:165` and `:172-185`, inside `subscribe()`:

  ```kotlin
      val supervisor = SupervisorJob(parent = this.coroutineContext[Job])
  ...
      val containment = CoroutineExceptionHandler { _, _ -> }
      val dispatcher =
        HandlerDispatcher(
          delegate = this.coroutineContext[ContinuationInterceptor] as? CoroutineDispatcher ?: Dispatchers.Default,
        )
      val supervised = CoroutineScope(this.coroutineContext + supervisor + containment + dispatcher)
      try {
        for (flow in handlers) {
          flow.launchIn(supervised)
        }
      } finally {
        dispatcher.starting = false
      }
      return supervisor
  ```

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt:55-69`, where A4's supervisor parent is the init launch's Job:

  ```kotlin
    init {
      viewModelScope.launch(Dispatchers.Default) {
        // Subscriptions start before init runs, so events init emits reach
        // them. Each step has its own error boundary, so a handled error in
        // one never skips the other.
        safeExecute(anchor, anchor.onDomainError, anchor.defect) {
          with(anchor) {
            subscribe()
          }
        }
        safeExecute(anchor, anchor.onDomainError, anchor.defect) {
          anchor.consumeInitial()
        }
      }
    }
  ```

- Two existing tests that throw a `CancellationException` by hand inside an active `runBlocking`. Both flip under spec Q3 (a).
  - `anchor/src/commonTest/kotlin/dev/kioba/anchor/ExecuteBoundaryTest.kt:84-103`, `` `CancellationException in execute is never swallowed` ``: `assertFailsWith<CancellationException> { safeExecute(...) { throw CancellationException("cancelled") } }`, then no handler call.
  - `anchor/src/commonTest/kotlin/dev/kioba/anchor/NonFatalTest.kt:24-35`, `` `CancellationException is fatal and rethrown even with defect handler` ``: the same pattern against `catchDefects`.
- Plan 003's pins in `anchor/src/commonTest/kotlin/dev/kioba/anchor/SubscriptionIsolationTest.kt` at `e8f6126`:
  - `:286` `an unhandled domain error inside an anchor action ends its listener without reaching defect`;
  - `:305` `a timeout inside an anchor action ends its listener without reaching defect`;
  - `:326` `a defect handler that throws ends the listener`, which asserts `listOf("action failed", "defect handler failed")`.

  The class KDoc (`:34-44`) lists these as listener-ending cases. Plan 003's branch comments each pin "Pins current behavior that plans/030 is expected to change".
- `docs/errors.md:41`: "Omitting `onDomainError` means an unhandled `raise()` crashes the coroutine. Omitting `defect` lets unexpected exceptions propagate normally." `:127-130`: "The `defect` handler runs for `orDie()` calls **and** for any uncaught `Throwable` from your action."
- Test harness patterns:
  - `withListeners` and `awaitLiveListeners` in `SubscriptionIsolationTest.kt:65-87` at `e8f6126`;
  - `inViewModel` in `InitEventDeliveryTest.kt:79-100`;
  - the execute mirror, which is native-safe: `CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> escaped.complete(e) }).launch { safeExecute(anchor, anchor.onDomainError, anchor.defect) { … } }`.

## Commands you will need

| Purpose | Command | Expected on success |
|---|---|---|
| New tests | `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.SilentEndingTest'` | Phase A: the tests marked FAIL in the Test plan fail and the rest pass. Phase B: all pass. |
| Core suite | `./gradlew :anchor:desktopTest` | exit 0 |
| Native | `./gradlew :anchor:iosSimulatorArm64Test` | exit 0, with no signal-6 abort |
| Test DSL | `./gradlew :anchor-test:allTests` | exit 0 (it inlines `safeExecute`) |
| Features | `./gradlew :features:config:allTests :features:main:allTests` | exit 0 |
| Docs bundle | `bash scripts/generate-llms-full.sh` | A second run leaves no diff |
| Full build | `./gradlew build` | exit 0 |

`--tests` binds only to the task in front of it. Give each task its own filter.

## Scope

**In scope**:
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/ErrorHandling.kt`: `catchDefects`, `safeExecute`, and a `@PublishedApi internal` classification helper.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/HandledEscape.kt` (create): the per-listener context element.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/SubscriptionDsl.kt`: the body of `.anchor {}`, the body of `anchorErrors()` from plan 003, and the KDoc of `.anchor {}`, `connect` and `anchorErrors`.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt`: the `catch` in `handlers()` and the launch loop in `subscribe()` only. `cancellable` is touched only in the conditional Step 10.
- `anchor/src/commonMain/kotlin/dev/kioba/anchor/viewmodel/ContainerViewModel.kt`: the `subscribe()` call in `init`, and the class KDoc.
- KDoc only: `RememberAnchorScope.kt` (`@param onDomainError`, `@param defect`) and `Raise.kt:10-11`.
- Tests:
  - `anchor/src/commonTest/kotlin/dev/kioba/anchor/SilentEndingTest.kt` (create);
  - `ExecuteBoundaryTest.kt` and `NonFatalTest.kt` (add U1–U4 and remove the two superseded tests);
  - `SubscriptionIsolationTest.kt` (flip the three pins and update the class KDoc).
- Docs: `docs/errors.md` (`:41`, `:127-131`), and `docs/llms-full.txt` (regenerated).

**Out of scope**:
- Restarting ended listeners (plan 003 decided against it).
- Making failures loud when no handler is configured (spec Q2).
- The anchor-test recording handlers (`AnchorTestScope.kt:72-84`), which is plan 006.
- `cancellable`, except the conditional Step 10.
- `NonFatal.kt` actuals: the classification wraps `isNonFatal()` and does not change it.

## Git workflow

- Branch: `fix/030-silent-subscription-endings`, from `fix/003-subscription-restart` (see Depends on).
- Commit style: gitmoji, for example:
  - `🧪 Reproduce silent endings from foreign cancellations, unhandled raise and init`
  - `🐛 Route foreign cancellations to defect`
  - `🐛 Escalate a domain error to defect when onDomainError is absent`
  - `🐛 Route an error handler's failure only once`
  - `🐛 Keep subscriptions alive whatever init does`
  - `📝 Document which errors reach defect`
- Do NOT push or open a PR unless the operator instructed it.

## Steps

### Phase A: reproduce and prototype (no production change is committed)

### Step 0: Base and baseline

Check out the base branch, run the drift check, then run `./gradlew :anchor:desktopTest :anchor:iosSimulatorArm64Test` and record both test counts. For reference, `e8f6126` had 118 desktop and 114 iOS tests.

Note whether `anchorErrors()` exists in `SubscriptionDsl.kt`. If it does not, skip T11 and the `anchorErrors()` parts of Steps 5 and 8, and say so in the PR.

**Verify**: both tasks exit 0.

### Step 1: Failing regression tests first

Create `SilentEndingTest.kt` with the tests T1–T15 from the Test plan. Condensed probe code is in spec Appendix A, and the harness helpers are listed in "Current state".

Rules:
- Assert the target behaviour. Where a test checks several facts, compare a `Pair`/`Triple` so that a failure reports all of them. Use `withTimeoutOrNull(1_000)` for waits that are expected to time out on the base, then assert.
- Never let a failure reach a coroutine that has no handler. Use `withListeners`/`inViewModel` with `finally` teardown, or the execute mirror.

Add U1–U4 to `ExecuteBoundaryTest.kt`/`NonFatalTest.kt`. **Do not remove** the two superseded originals yet.

**Verify**:
- The new-tests command gives FAIL for T1–T4, T6–T8, T10, T14, U3 and U4, and PASS for T5, T9, T12, T13, T15, U1 and U2. That matches the spec probes.
- T10b and T11 are predicted to FAIL. They were not run for the spec, so record their actual result either way.
- Run `./gradlew :anchor:iosSimulatorArm64Test` and check it gives the same pattern with no signal 6.
- Commit the tests only.

### Step 2: Prototype (do not commit)

Apply the sketches from Steps 3–6 using the *recommended* options. Then run the core suite, native, anchor-test and features.

**Verify**:
- Every test in `SilentEndingTest` passes, and U1–U4 pass.
- The expected failures are the three plan-003 pins and the two superseded originals (`ExecuteBoundaryTest` `` `CancellationException in execute is never swallowed` `` and `NonFatalTest` `` `CancellationException is fatal and rethrown even with defect handler` ``).
- Nothing else fails. That includes plan 003's `an inner flow that flatMapLatest cancels is not routed` and `cancelling the subscribing scope is not routed`, all of `InitEventDeliveryTest`, and `EventBusReentrancyTest`. The spec's prototype saw exactly this (spec §3.7).
- Write a results table (test × base/prototype) into the report.
- Revert: `git diff --stat -- anchor/src/commonMain` is empty.

### STOP: maintainer GO required

Report the Step 1 and Step 2 results and ask for answers to spec Q1 (A2 routing), Q2 (no handler), Q3 (A1 scope), Q4 (what `defect` receives), Q6 (A4 in this plan) and Q7 (release). Mention Q5 (`cancellable`) as conditional. **Do not start Phase B without an explicit GO naming the options.**

If the maintainer declines an option, turn its regression tests into pins of the behaviour being kept, and list them in the PR:
- Q1 (d): T6–T8 and U4;
- Q3 (b): T2 and U3;
- Q6 no: T14.

The Done criteria then apply to the tests as they stand after that.

### Phase B: implement the chosen options

### Step 3: A1, classify cancellation by the running coroutine (Q3, Q4)

In `ErrorHandling.kt`, change `catchDefects` to use the classification helper:

```kotlin
public suspend inline fun <R, S, Err> catchDefects(
  anchor: Anchor<R, S, Err>,
  noinline defect: (suspend ErrorScope<R, S>.(Throwable) -> Unit)?,
  block: () -> Unit,
) where R : Effect, S : ViewState, Err : Any {
  try {
    block()
  } catch (e: Throwable) {
    if (!e.isRoutableDefect()) throw e
    defect?.invoke(anchor, e) ?: throw e
  }
}

/**
 * Whether [catchDefects] may hand this to `defect`. A [CancellationException]
 * caught while the running coroutine is still active does not come from
 * cancelling it (a `withTimeout` expiry, awaiting a `Deferred` cancelled
 * elsewhere), so it is a failure like any other. The coroutine's own
 * cancellation and a [RaisedException] never are.
 */
@PublishedApi
internal suspend fun Throwable.isRoutableDefect(): Boolean =
  when (this) {
    is RaisedException -> false
    is CancellationException -> currentCoroutineContext().isActive
    else -> isNonFatal()
  }
```

- If Q3 is (b), the cancellation branch becomes `this is TimeoutCancellationException && currentCoroutineContext().isActive`.
- If Q4 chooses a wrapper, implement it at the `defect?.invoke` call only.
- Imports: `kotlinx.coroutines.currentCoroutineContext`, `kotlinx.coroutines.isActive`, `kotlin.coroutines.cancellation.CancellationException`.
- Then remove the two superseded originals (U1 and U2 carry their intent forward).

**Verify**:
- T1–T4 and U3 pass.
- T5, U1 and U2 still pass.
- `:anchor:desktopTest` has no failures apart from the pins handled in Step 7.

### Step 4: A2, escalate an unhandled domain error (Q1, Q2)

With Q1 (a), change `safeExecute` as follows. `catchDomainError` stays: it is public API and not used here any more.

```kotlin
  catchDefects(anchor, defect) {
    try {
      block()
    } catch (e: RaisedException) {
      @Suppress("UNCHECKED_CAST")
      val error = e.error as Err
      when {
        onDomainError != null -> onDomainError.invoke(anchor, error)
        // Escalate as orDie(error) would. Thrown inside catchDefects' block,
        // so a defect handler that throws is not invoked a second time.
        defect != null -> throw DomainDefectException(error)
        else -> throw e
      }
    }
  }
```

If Q1 is (d), skip this step and keep only the Step 8 docs.

**Verify**:
- T6–T8 and U4 pass.
- T9 still passes: with no handlers, the action stops silently, per Q2.
- `ExecuteBoundaryTest` `` `missing onDomainError causes rethrow` `` and `` `RaisedException never reaches defect handler` `` still pass.

### Step 5: A3, route a handler's failure only once

Create `internal/HandledEscape.kt`:

```kotlin
/**
 * Per-listener record of the exception that last escaped an `.anchor {}` step
 * or `anchorErrors()`. That exception already had its turn at `onDomainError`
 * and `defect` (or had no matching handler), so the chain-level `catch` in
 * `AnchorRuntime.handlers()` must not route it again. Without the record, a
 * `defect` handler that throws is invoked a second time with its own
 * exception. Only the last escape is kept: two steps of one listener failing
 * concurrently (e.g. under `merge`) can still route the earlier one twice.
 */
internal class HandledEscape : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<HandledEscape>

  @Volatile
  private var last: Throwable? = null

  fun record(e: Throwable) {
    last = e
  }

  fun isRecorded(e: Throwable): Boolean = last === e
}

internal suspend fun recordHandledEscape(e: Throwable) {
  currentCoroutineContext()[HandledEscape]?.record(e)
}

internal suspend fun isHandledEscape(e: Throwable): Boolean =
  currentCoroutineContext()[HandledEscape]?.isRecorded(e) == true
```

Then make four edits:
- **`.anchor {}`**: wrap the `safeExecute` call in `try { … } catch (e: Throwable) { recordHandledEscape(e); throw e }`.
- **`anchorErrors()`** (plan 003): at the top of its `catch`, add `if (isHandledEscape(error)) throw error`, so an escape from an inner `.anchor {}` is not routed. Then wrap its `safeExecute` the same way as `.anchor {}`.
- **`handlers()`**: add `if (isHandledEscape(e)) throw e` as the first line of the `catch`.
- **`subscribe()`**: replace `flow.launchIn(supervised)` with `supervised.launch(HandledEscape()) { flow.collect {} }`. Use the same scope and default start, so the `HandlerDispatcher` in-place start is unchanged. Drop the `launchIn` import if it is now unused.

**Verify**:
- T10 and T11 pass, and T12 and T13 still pass.
- T10b must pass **on desktop**. There, `-ea` turns on kotlinx debug mode and stack-trace recovery, which may hand the chain `catch` a *copy* of the escaped exception, with the original as `cause`. If T10b fails on desktop but passes on iOS, extend `isRecorded` to `last === e || (last != null && e.cause === last)` and re-run. If it still fails, STOP.
- All of `InitEventDeliveryTest` still passes.

### Step 6: A4, subscriptions outlive `init` (Q6)

In `ContainerViewModel.init`, change only the `subscribe()` call:

```kotlin
        safeExecute(anchor, anchor.onDomainError, anchor.defect) {
          with(anchor) {
            // Parent the listeners to the ViewModel, not to this coroutine:
            // however init ends, including a cancellation safeExecute
            // rethrows, it must not cancel them. Still Dispatchers.Default.
            CoroutineScope(currentCoroutineContext() + viewModelScope.coroutineContext.job).subscribe()
          }
        }
```

The prototype used `viewModelScope.coroutineContext[Job]!!`; `.job` is equivalent. Imports: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.currentCoroutineContext`, `kotlinx.coroutines.job`.

Extend the class KDoc (`:17-27`) with this sentence: subscriptions belong to the ViewModel, so nothing `init` does, not even an unrouted failure, cancels them.

**Verify**:
- T14 passes, T15 still passes, and T4 and T8 still pass.
- All of `InitEventDeliveryTest` still passes, including `a handler that ends before subscribing does not hold up init`.

### Step 7: Flip plan 003's pins

In `SubscriptionIsolationTest.kt`:
- rewrite `:286` as `a domain error with only defect configured is routed and the listener survives` (Q1 (a));
- rewrite `:305` as `a timeout inside an anchor action is routed to defect and the listener survives`;
- rewrite `:326` as `a defect handler that throws is invoked once and ends the listener`, asserting `listOf("action failed")` and `awaitLiveListeners(0)`.

Remove the three "Pins current behavior that plans/030 …" comments, and fix the class KDoc (`:34-44`), which lists these cases as listener-ending.

**Verify**: `./gradlew :anchor:desktopTest` exits 0.

### Step 8: Document the rules

- **`.anchor {}` KDoc**:
  - an error is routed per value; a domain error with no `onDomainError` goes to `defect` as `DomainDefectException` (Q1 (a));
  - a cancellation that does not come from cancelling the listener, such as `withTimeout`, goes to `defect`;
  - with no matching handler, the listener ends.
- **`connect` and `anchorErrors` KDoc**: an exception thrown by `onDomainError` or `defect` is never routed again; it ends the listener.
- **`RememberAnchorScope.create`**:
  - `@param defect`: also receives cancellations that do not come from cancelling the anchor, and domain errors raised while `onDomainError` is absent (as `DomainDefectException`);
  - `@param onDomainError`: add the matching sentence.
- **`Raise` KDoc (`Raise.kt:10-11`)**: what happens without `onDomainError`: `defect` if configured, otherwise the action stops silently.
- **`docs/errors.md:41`**: replace "crashes the coroutine" with the actual rule: `defect` if configured, else silent (Q2); and without `defect`, a non-fatal exception reaches the platform's uncaught handler. Add a note to the warning box at `:127-131` that `withTimeout` expiries reach `defect`.
- **Plan 028**: if it has landed, reconcile its W12 text and `guarantees.md`. Otherwise, leave a note in the PR for 028.
- Run `bash scripts/generate-llms-full.sh` twice.

**Verify**: the second run of the script leaves no diff, and `./gradlew :anchor:desktopTest` exits 0 (KDoc only).

### Step 9: Native, test DSL, features, full build

**Verify**: `./gradlew :anchor:iosSimulatorArm64Test` (no signal 6), `./gradlew :anchor-test:allTests`, `./gradlew :features:config:allTests :features:main:allTests` and `./gradlew build` all exit 0.

### Step 10 (conditional, Q5): `cancellable` captures a foreign cancellation

Do this step only with a GO on Q5 **and** once PR #278 (plan 026) has merged into the base. Otherwise skip it and note it in the PR.

In `cancellable`'s launched block, next to `catch (e: RaisedException) { raised = e; throw e }`, capture a `CancellationException` caught while `currentCoroutineContext().isActive`, and rethrow it after `join()` the same way as `raised`. Add the spec's "A1 cancellable" probe as T16.

**Verify**:
- T16 passes, and it failed before the change.
- 026's regression tests, and the cancellable tests (`CancellableTest`, `CancellableBasicTest`, `JobIdentity*`), still pass. A superseded same-key job must still end silently.

## Test plan

**Test-first.** Every regression test below fails on the base, and every guard passes before and after. "Evidence" names the spec probe that showed the base result. In spec Appendix A the probe names are prefixed `A1`–`A4`.

| # | Test (`SilentEndingTest` unless noted) | Base | Evidence |
|---|---|---|---|
| T1 | a timeout inside an anchor action reaches defect and the listener survives | FAIL | A1 sub, timeout: `([], [])` |
| T2 | awaiting a Deferred cancelled elsewhere inside an anchor action reaches defect | FAIL | A1 sub, foreign await: `([], 0)` |
| T3 | a timeout inside an executed action reaches defect (`ContainerViewModel.execute`) | FAIL | A1 execute: `[]` |
| T4 | a timeout in init reaches defect and leaves subscriptions attached | FAIL | A1 init: `(0, 0, 0)` |
| T5 | clearing the ViewModel while an action is suspended does not reach defect | PASS (guard) | guard, passes |
| T6 | a domain error with only defect configured reaches defect as DomainDefectException and the listener survives | FAIL | A2 sub: `([], [])` |
| T7 | a domain error in an executed action with only defect configured reaches defect | FAIL | A2 execute: `[]` |
| T8 | a domain error in init with only defect configured reaches defect and leaves subscriptions attached | FAIL | A2 init: `(0, 0, 0)` |
| T9 | a domain error with no handlers still stops the action silently (execute mirror: job cancelled, nothing escapes) | PASS (guard, Q2) | characterization, passes |
| T10 | a defect handler that throws inside an anchor action is invoked once | FAIL | A3 sub: two calls |
| T10b | the same, with `.anchor {}` inside a `flatMapLatest` inner flow | FAIL (predicted) | not run |
| T11 | a defect handler that throws inside `anchorErrors()` is invoked once | FAIL (predicted) | not run (`anchorErrors` was uncommitted) |
| T12 | a throwing defect handler in an executed action runs once and its exception escapes (execute mirror) | PASS (guard) | characterization, passes |
| T13 | an onDomainError that throws is routed to defect once and the listener survives | PASS (guard) | guard, passes |
| T14 | a domain error in init with no handlers leaves subscriptions attached | FAIL | A4 init: `(0, 0)` |
| T15 | a clean init leaves handlers attached | PASS (control) | control: `(1, 1)` |
| U1 | `ExecuteBoundaryTest`: cancelling the coroutine running safeExecute is never routed (`launch`, `cancelAndJoin` while suspended in the block) | PASS | replaces `:85` |
| U2 | `NonFatalTest`: the coroutine's own cancellation is rethrown even with a defect handler | PASS | replaces `:25` |
| U3 | `ExecuteBoundaryTest`: a CancellationException thrown while the coroutine is active reaches defect | FAIL | same mechanism as T3 |
| U4 | `ExecuteBoundaryTest`: an unhandled RaisedException with only defect reaches it as DomainDefectException | FAIL | same mechanism as T7 |

- Patterns: `SubscriptionIsolationTest` (listeners), `InitEventDeliveryTest` (ViewModel) and `ExecuteBoundaryTest` (direct `safeExecute`).
- Regression:
  - the full `:anchor` desktop and iOS-simulator suites;
  - plan 003's `AnchorErrorsTest`, whose cancellation tests are this design's guard;
  - `:anchor-test:allTests`, the features' tests, and `./gradlew build`.

## Done criteria

- [ ] T1–T4, T6–T8, T10, T14, U3 and U4 failed before the fix (output recorded in the PR), and all T and U tests pass after it
- [ ] The T10b and T11 base results are recorded
- [ ] The maintainer GO is recorded, naming the options for Q1–Q4, Q6 and Q7 (and Q5 if Step 10 runs)
- [ ] Plan 003's three pins are flipped, and the two superseded tests are replaced by U1/U2
- [ ] `:anchor:desktopTest`, `:anchor:iosSimulatorArm64Test`, `:anchor-test:allTests`, the features' tests and `./gradlew build` exit 0
- [ ] The KDoc and `docs/errors.md` state the rules, and `docs/llms-full.txt` is regenerated
- [ ] The PR lists the behaviour changes for plan 027's CHANGELOG (spec §4)
- [ ] The `plans/README.md` status row is updated

## STOP conditions

Stop and report back if:

- **Base results**:
  - any test marked FAIL with evidence (T1–T4, T6–T8, T10, T14) passes on the base, or any guard fails on the base. The analysis has drifted.
  - T10b or T11 passes on the base is **not** a STOP. Record it; the A3 record is then needed only for the direct case.
- **Prototype**: it breaks a test other than the three pins and the two superseded originals. The broken test may encode a contract; report which one.
- **Step 5 identity**: T10b still fails on desktop after the `cause` comparison.
- **Native**: a run aborts with signal 6. A test leaked a failing coroutine. Fix the test's teardown, not production code.
- **GO**: no maintainer GO has been given at the Phase A/B boundary.
- **Base drift**:
  - #273 or plan 003 changed `ContainerViewModel.init`, `subscribe()`'s launch loop or `safeExecute` beyond the excerpts;
  - or the base has no `SupervisorJob`/containment in `subscribe()`.

## Maintenance notes

- **Reviewer checks**:
  - the coroutine's own cancellation never reaches `defect`: T5, U1, U2, and plan 003's `an inner flow that flatMapLatest cancels is not routed` and `cancelling the subscribing scope is not routed`;
  - no production code launches a coroutine without the containment handler.
- **Q2 revisited**: if the maintainer later wants failures to be loud when no handler is configured, `viewModelScope` launches need a `CoroutineExceptionHandler` first, or Kotlin/Native aborts the process.
- **The escape record is per listener.** An `.anchor {}` whose flow is collected outside `subscribe()`, for example via `shareIn` into another scope, has no record and keeps the old double invocation. Document it if anyone asks.
- **anchor-test**: it still records an unhandled `raise` as a domain error, because its recording handler is never null (spec §1.3). That belongs to plan 006.
- **Plan 028**: its W12 text for `errors.md:41` must describe the post-030 rule (spec §3.6).
- **`cancellable`**: if Step 10 was skipped, its foreign-cancellation gap (spec §3.5) is still open. File it with plan 026's follow-ups.
