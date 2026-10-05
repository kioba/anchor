# Spec: `emit {}` from an action run by a `connect()` handler wedges the anchor's event bus (issue #140, part c)

- **Issue**: found while verifying [#140](https://github.com/kioba/anchor/issues/140) ("Event vs Signal", "What synchronization guarantees exist?"). There is no dedicated issue yet. Recommend the maintainer open one.
- **Verdict**: CONFIRMED (reproduced on `0bc430c`).
- **Severity**: P1. It is a correctness bug in documented usage, and it fails silently. `docs/concepts.md:88` presents events as "*useful for complex logic where one action triggers another*". Once triggered, **every later `emit {}` on that anchor suspends forever**, with no exception and no log. Workarounds exist: `events.buffer().anchor { ... }`, or not emitting from handler actions.
- **Verified at**: origin/master `0bc430c`, 2026-09-29, with probes in a disposable worktree that has since been removed.
- **Plan**: [`plans/023-event-emit-deadlock.md`](../023-event-emit-deadlock.md). It uses Investigate-then-Act, because choosing the buffer semantics needs a maintainer decision.
- **Related**:
  - Plan 004 (init event delivery, same `_emitter`).
  - Plan 003 (subscription resilience).
  - [028](028-docs-overhaul.md): its `guarantees.md` "Known issue" admonition and pinning test 6 depend on this spec.

---

## 1. Problem statement and evidence

### 1.1 Mechanism

- The event bus is an **unbuffered** shared flow (`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:62-64`):

  ```kotlin
  @PublishedApi
  internal val _emitter: MutableSharedFlow<Event> = MutableSharedFlow()
  ```

- `emit {}` suspends on it (`AnchorRuntime.kt:229-233`): `_emitter.emit(SubscriptionScope.block())`.
- Every `connect<A>` handler is its own collector of the **whole** bus, filtered downstream:
  - `SubscriptionDsl.kt:68-74` is `wrap { block(filterIsInstance()) }`, and `AnchorRuntime.kt:85-88` adds `.onSubscription { emit(Created) }`.
  - Each handler's flow is launched separately (`AnchorRuntime.kt:111-127`).
- `.anchor { action }` runs the action **inline, inside that collector** (`SubscriptionDsl.kt:46-53`): `onEach { value -> safeExecute(anchor, onDomainError, defect) { anchor.action(value) } }`.
- kotlinx.coroutines 1.11.0, `flow/SharedFlow.kt:74-78`:

  > A default implementation of a shared flow that is created with `MutableSharedFlow()` constructor function without parameters has no replay cache nor additional buffer. [emit] call to such a shared flow suspends until all subscribers receive the emitted value and returns immediately if there are no subscribers.

- So when an action running inside handler H calls `emit {}`, the emission waits for **all** subscribers to receive it, including H. H cannot receive anything until its current `onEach` returns, and that `onEach` is waiting on the emission, so the two wait on each other. The unconsumed slot stays at the head of the bus, and every later emission from any action queues behind it.
- The same happens when `onDomainError` or `defect` calls `emit {}` for an error raised inside a `.anchor {}` action, because `safeExecute` runs those handlers inline too (`internal/ErrorHandling.kt:36-47`).
- The in-repo sample is **not** affected: `features/main/.../MainSubscriptions.kt:23-33` puts `flatMapLatest` before `.anchor(...)`, which collects upstream in a separate coroutine.

### 1.2 Evidence (probes, desktop JVM, `0bc430c`)

Probe P1 subscriptions: `connect<Ping> { it.anchor { pingHandled++; emit { Pong } } }` and `connect<Pong> { it.onEach { pongs.add(1) } }`. The probe emits Ping from outside, then tries two more emits, each with a timeout:

```
PROBE1 pingHandled=1 pongs=1
PROBE1 unrelated emit completed within 1s: false
PROBE1 second Ping emit completed within 1s: false, pingHandled=1
```

| Probe | Setup | Result |
|-------|-------|--------|
| P1 | above | Pong is delivered, but the Ping handler's `emit` never returns. An unrelated `emit {}` **times out**, and the second Ping **times out**. The bus is wedged. |
| P1c | `onDomainError = { emit { Pong } }` and `connect<Ping> { it.anchor { raise(E) } }` | `handled=1 pongs=1 laterEmitCompleted=false`. The error-handler path wedges too. |
| P1b | P1 with `events.buffer().anchor { ... }` | `otherDone=true secondPing=true pingHandled=2 pongs=2`. The workaround works. |
| P6 | Handler busy with event 1 (gate closed); `emit` event 2 from outside | Not returned within 300 ms. It returns after the gate opens. This is today's back-pressure, and it is by design. |
| Prototype (a) | `_emitter = MutableSharedFlow(extraBufferCapacity = 64)` | P1: `unrelated emit completed within 1s: true`, `second Ping ... true`. `./gradlew :anchor:desktopTest --rerun` gives 95 tests and 0 failures, including `SubscriptionDslTest`'s sibling-isolation test. |

The probe code is in [028 spec, Appendix A](028-docs-overhaul.md#appendix-a-probe-code-condensed-all-run-on-0bc430c), P1/P1b/P6.

### 1.3 Why it matters

- It is silent: no exception, no `defect` call, no log. The only symptom is that later event-driven behaviour stops.
- It is permanent for the anchor's lifetime; only clearing the ViewModel cancels the stuck coroutines.
- The documented purpose of events is chaining one action to another (`docs/concepts.md:88`), and emitting from a handler is the natural way to do that.

---

## 2. Goals and non-goals

**Goals**
- An `emit {}` from an action (or error handler) running inside a `connect()` handler never blocks the bus.
- Keep per-handler ordering (FIFO) and fan-out: every attached handler sees every event.
- Never drop events silently while handlers are attached.
- Native-safe: no new fire-and-forget coroutines under a `SupervisorJob` without a `CoroutineExceptionHandler`.

**Non-goals**
- Events emitted before any handler attaches, including those from `init` (plan 004).
- Restarting a handler killed by an exception (plan 003's re-scope).
- Signal semantics (#266).

---

## 3. Design options

| Option | Sketch | Deadlock fixed? | Ordering / fan-out | Back-pressure | Memory | Verdict |
|---|---|---|---|---|---|---|
| **(a) Bounded buffer** | `MutableSharedFlow<Event>(extraBufferCapacity = 64)` | Yes, while fewer than 64 events are pending behind the slowest handler (prototype passes) | Kept | Only once the buffer is full | ≤ 64 refs | **Recommended** |
| (b) Unbounded buffer | `extraBufferCapacity = Int.MAX_VALUE` | Always | Kept | None | Unbounded if a handler is stuck | Alternative if Q1 = "never suspend" |
| (c) Drop on overflow | `onBufferOverflow = DROP_OLDEST` | Always | Drops | None | Bounded | Rejected: events drive logic, so silent drops are unacceptable |
| (d) Launch each `.anchor {}` action | `onEach { launch { action } }` | Yes | **Breaks** per-handler ordering and adds concurrency inside a handler | — | — | Rejected: changes `.anchor` semantics and needs a handler on Native |
| (e) Detect and throw | Handler collectors carry a context marker; `emit` throws if called under the marker | Turns the hang into a failure | — | — | — | Rejected as the fix, because it crashes (aborts on Native). It could be a debug-only aid |
| (f) Per-handler `Channel` pump | Each `connect` gets `Channel(64)` fed by one pump coroutine | Yes | Kept | Per handler | Per-handler buffers | Rejected: equivalent to (a) with more code and one extra launched coroutine per anchor |
| (g) Document only | Recommend `.buffer()` | No | — | — | — | Interim only (028 admonition) |

Remaining limit of (a): one handler invocation that emits **more than 64** events to a bus it is itself subscribed to still wedges once the buffer fills. That case is bounded and documentable, and the KDoc should state it.

### 3.1 Interactions

- **Plan 004** (subscribe before `init`). It waits on `_emitter.subscriptionCount`, which (a) does not change. The two are compatible.
- **Test runtime.** `AnchorTestRuntime.emit` only records (`AnchorTestRuntime.kt:45-49`), so it is unaffected.
- **028 pinning test 6** ("emit waits while a handler is busy") **flips** under (a) and (b). The same PR updates it and `guarantees.md#events`.
- **Native.** (a) and (b) launch nothing new.

---

## 4. Behaviour and API changes

- **Non-breaking** API: no signature changes.
- Behaviour:
  - `emit {}` returns as soon as the event is buffered for all attached handlers. Today it waits until each handler has *taken* the event. Actions no longer wait on slow handlers until 64 events are pending.
  - No guarantee is lost. Even today, `emit` returning did not mean a handler had *finished* processing.
- KDoc on `SubscriptionAnchor.emit` (`Anchor.kt:239-257`) states the new contract, and so do the 028 docs.

## 5. Acceptance criteria

1. The regression test *"an action run by a connect handler can emit without blocking later emits"* fails (times out) on `0bc430c` and passes after the fix.
2. The regression test *"onDomainError can emit for an error raised in a handler action"* fails on `0bc430c` and passes after the fix.
3. Guard tests pass before and after the fix:
   - per-handler FIFO order for 100 events;
   - two handlers each receive every event;
   - `Created` is received once per handler.
4. `./gradlew :anchor:desktopTest :anchor:iosSimulatorArm64Test` and `./gradlew build` exit 0.
5. The `emit` KDoc states the contract and the 64 limit (if (a)). If 028 has landed, `guarantees.md#events` and its pinning test 6 are updated in the same PR.

## 6. Open questions for the maintainer

- **Q1.** Should `emit {}` ever suspend? Choose (a) "bounded, suspend only when 64 are pending" (recommended) or (b) "never suspend, unbounded".
- **Q2.** Should the capacity be 64 (the same as signals) or configurable in `create(...)`? Recommended: a fixed internal constant.
- **Q3.** Is it worth adding a debug-only detector (e) for the >64 self-emission case, or is documenting it enough?
- **Q4.** Ship in 0.1.9 (unreleased), or later?

## 7. Relationship to existing plans

| Plan | Relationship |
|---|---|
| 004 | Complements. The same `_emitter`, and a different defect (no subscriber yet). They are independent, with a trivial rebase. |
| 003 | Adjacent (handler resilience). No overlap. |
| 028 | Its interim "Known issue" docs and pinning test 6 are replaced or flipped by this plan. |
| #266 spec | Signals only. No overlap. |
