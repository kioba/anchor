# Spec: Signal delivery when no collector is attached (issue #266)

- **Issue**: [#266](https://github.com/kioba/anchor/issues/266) — "Signals posted from `init` are dropped before a collector attaches — #144 not fixed by `extraBufferCapacity`"
- **Verdict**: CONFIRMED (reproducer fails exactly as reported; the "Related" conflation claim is also confirmed, using the real `HandleSignal`)
- **Severity**: P1 (high). A core-contract correctness bug that users hit in normal use (a signal posted from `init`, during rotation or while backgrounded is silently lost). A workaround exists: model the outcome in state.
- **Verified at**: origin/master `0bc430c`, 2026-09-29, in a disposable worktree (removed afterwards)
- **Plan**: [`plans/024-init-signal-delivery.md`](../024-init-signal-delivery.md)
- **Supersedes**: plan 017 (this spec answers all four of its sections; see §7)
- **Depends on**: plan 002 (consumer-side conflation fix, delegated) → plan 001 (harness)
- **Complements**: plan 004 (the same "posted from init" defect, for `Event`s)

---

## 1. Problem statement and evidence

### 1.1 Mechanism

`post {}` emits into a `SharedFlow` with no replay
(`anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/AnchorRuntime.kt:57-60,222-227`):

```kotlin
internal val _signals: MutableSharedFlow<SignalProvider> =
  MutableSharedFlow(extraBufferCapacity = 64)
...
override suspend fun post(block: SignalScope.() -> Signal) {
  val signal = SignalScope.block()
  _signals.emit(SignalProvider { signal })
}
```

The kotlinx.coroutines 1.11.0 `SharedFlow` KDoc (`flow/SharedFlow.kt:66-71`) says:

> **Buffer overflow condition can happen only when there is at least one subscriber that is not ready to accept
> the new value.** In the absence of subscribers only the most recent `replay` values are stored and the buffer
> overflow behavior is never triggered and has no effect. [...] Essentially, the behavior in the absence of
> subscribers is always similar to [BufferOverflow.DROP_OLDEST], but the buffer is just of `replay` size (without
> any `extraBufferCapacity`).

Here `replay = 0`, so any `post` made while nobody is collecting is discarded. The `extraBufferCapacity = 64`
added by #152 (commit `1ee66f2`) has no effect in that case, so #144 was closed without being fixed.

### 1.2 Reproduction (the issue's test, verbatim)

I placed `InitSignalDeliveryTest` at `anchor/src/commonTest/kotlin/dev/kioba/anchor/` and ran
`./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.InitSignalDeliveryTest'`:

```
InitSignalDeliveryTest[desktop] > signal posted from init is delivered to a collector that subscribes afterwards[desktop] FAILED
1 test completed, 1 failed
message="java.lang.AssertionError: Signal posted from init was dropped: no subscriber, replay = 0"
```

The run was repeated twice with `--rerun`, and it failed identically each time.

### 1.3 Today's guarantees: truth table

Each row is backed either by a probe test run on `0bc430c` or by quoted library documentation. Every probe
asserts today's behaviour and passes. Appendix A has the probe list.

| # | Situation | Today | Evidence |
|---|-----------|-------|----------|
| T1 | `post` from `init` through `ContainerViewModel`, with the UI collector attaching about one frame later | **dropped** | probe P1 (`ContainerViewModel(runtime)`, `delay(16)`, then `signals.first()` returns null); code path in §1.6 |
| T2 | `post` before the first composition, using the real `HandleSignal` | **dropped** | probe H3 (real `HandleSignal` in a headless composition) |
| T3 | `post` while the lifecycle is below `STARTED` (backgrounded, or `ON_STOP` during rotation) | **dropped** | probe H4: collector detaches (`subscriptionCount` 0), the signal is emitted, then the lifecycle goes back to `RESUMED` and nothing is handled. `collectAsStateWithLifecycle` KDoc (androidx lifecycle-runtime-compose 2.10.0-rc01 desktop sources, `FlowExt.kt:154-155`, `Flow<T>` overload): "*The collection stops when [lifecycle] falls below [minActiveState]*" |
| T4 | Live `post` with N collectors attached | **fan-out**: each collector gets it | probe P2 (2 collectors, both receive) |
| T5 | Burst of 3 `post`s within one frame reaching one `HandleSignal` | **conflated**: only the last one is handled | probe H1 (real `HandleSignal`: `received == [3]`) |
| T6 | Slow `HandleSignal` handler still running when the next signal arrives | **cancelled mid-flight** | probe H2: `started == [1, 2]`, `completed == [2]` |
| T7 | Burst above 64 with a collector attached but not ready | `post` **suspends** (default `BufferOverflow.SUSPEND`) | SharedFlow KDoc above, which applies only when a subscriber exists |

T1–T3 are the subject of #266. T5–T6 are the issue's "Related" claim and are owned by plan 002 (§3.5).

### 1.4 Why it matters

- **The default path is racy.** `init` runs as soon as the ViewModel is constructed (§1.6), while `HandleSignal`
  starts collecting only after the first composition has been applied. Whether a signal posted from `init`
  arrives depends on how long `init` takes: a cached or synchronous `init` always loses it.
- **The library's own recommended pattern is affected.** The `ErrorScope` KDoc
  (`anchor/src/commonMain/kotlin/dev/kioba/anchor/ErrorScope.kt:14`) shows
  `defect = { throwable -> post { ErrorSignal(throwable) } }`. `ContainerViewModel` runs `init` inside
  `safeExecute(anchor, onDomainError, defect)` (`ContainerViewModel.kt:46`), so an error raised in `init` and
  reported through this pattern is dropped. That is exactly the case where the user most needs feedback.
- **Nothing in the docs warns about it.** `docs/concepts.md:68-84`, `docs/compose.md:106-123`, the `post` KDoc
  (`Anchor.kt:269-283`) and the `HandleSignal` KDoc (`LocalSignal.kt:23-38`) say nothing about delivery, and
  #144 reads as fixed.
- **T3 also drops signals outside `init`.** A signal posted by a background job while the app is stopped is lost
  today, not delayed.

### 1.5 The "Related" claim: HandleSignal conflation — CONFIRMED

`HandleSignal` (`anchor-compose/src/commonMain/kotlin/dev/kioba/anchor/compose/LocalSignal.kt:41-52`) turns the
stream into a single latest value and then keys a restartable effect on that value:

```kotlin
val signals by LocalSignals.current.collectAsStateWithLifecycle(null)
val update = rememberUpdatedState(block)
LaunchedEffect(signals) { ... }
```

`collectAsStateWithLifecycle` is `produceState { lifecycle.repeatOnLifecycle(minActiveState) { collect { this@produceState.value = it } } }`
(lifecycle-runtime-compose 2.10.0-rc01, `FlowExt.kt:176-182`). Every element overwrites one snapshot `State`,
and recomposition reads only the value that is current at frame time.

There is no Compose test harness yet (plan 001 is TODO), so I built a headless one: `Recomposer`,
`BroadcastFrameClock`, a no-op `AbstractApplier`, `LifecycleRegistry.createUnsafe`, and `Dispatchers.setMain`,
because `repeatOnLifecycle` switches to `Dispatchers.Main.immediate` (`RepeatOnLifecycle.kt:83`). I then drove
the **real** `HandleSignal` with it. Probe H0 (one signal is handled) validates the harness. H1 and H2 prove
T5 and T6 deterministically; all three passed three times in a row.

### 1.6 Ordering: ContainerViewModel vs the UI collector

`ContainerViewModel.kt:44-53`:

```kotlin
init {
  viewModelScope.launch(Dispatchers.Default) {
    safeExecute(anchor, anchor.onDomainError, anchor.defect) {
      anchor.consumeInitial()
      with(anchor) { subscribe() }
    }
  }
}
```

The order is:

1. `init` is launched on `Dispatchers.Default` when the ViewModel is constructed. On Compose that happens inside
   `viewModel(...)` during the first composition (`anchor-compose/.../RememberAnchor.kt:157-161`).
2. `subscribe()` attaches the **event** listeners after `init` finishes. That ordering is plan 004's defect for
   `Event`s. It does not affect signals.
3. The **signal** collector (`HandleSignal`) attaches only when its effect runs after the composition is applied.
   That is strictly later than step 1, and the two steps run on different threads.

Nothing orders `post` in step 1 before or after the attach in step 3.

### 1.7 iOS path: same drop; wider window only for `.task {}` consumers

- `nativeSignals()` wraps the same replay-0 flow (`anchor/src/iosMain/kotlin/dev/kioba/anchor/RememberAnchor.kt:56-57`).
- `rememberAnchor` constructs the `ContainerViewModel` (`iosMain/.../RememberAnchor.kt:33-38`), which launches
  `init` immediately (§1.6). This happens *before* Swift calls `nativeSignals().collect`
  (`iosApp/iosApp/ViewModelProtocol.swift:34-52`).
- `NativeSharedFlow.collect` does not subscribe synchronously. It calls `scope.launch` on `Dispatchers.Main`
  (`iosMain/.../NativeFlows.kt:64-67`). The kotlinx.coroutines docs say Main "*on Native Darwin-based targets
  [...] is a dispatcher backed by Darwin's main queue*" (`Dispatchers.common.kt:29`), and
  `CoroutineStart.DEFAULT` "*immediately schedules the coroutine for execution according to its context*"
  (`CoroutineStart.kt:17`). So the subscription happens on a later main-queue turn.
- **Same drop: yes.** In the in-repo sample, the window lasts from ViewModel construction to the next main-queue
  turn, which is comparable to the Compose first-frame window. Consumers that start collecting from SwiftUI
  `.task {}` or `onAppear`, as the issue's reporter does, subscribe only after the view appears, so their
  window is **wider**. The issue's "window is wider" claim holds for that usage, not for the in-repo sample.
- **iOS consumer-side conflation.** The Swift sample funnels every signal into one `@Published var signal`
  (`ViewModelProtocol.swift:28,50-52`), which is read by `.onChange(of:)` (`CounterView.swift:55`). That is the
  same latest-value-holder shape as T5 (probe P3 models it). This is sample code, not published API, and it
  belongs to plan 019. I did not run it on a simulator.
- **Native constraint for any fix:** a fire-and-forget coroutine under a `SupervisorJob` without a
  `CoroutineExceptionHandler` aborts the process on Kotlin/Native (commit `687d4e5`). The recommended design
  (§3.2) launches **no** coroutines of its own. All of its work runs inside the collector's or the poster's
  coroutine.

### 1.8 Adjacent finding (out of scope; route to plan 004 or a new issue)

`consumeInitial()` and `subscribe()` share **one** `safeExecute` block (`ContainerViewModel.kt:46-50`). If `init`
raises a domain error that `onDomainError` handles, the block exits and `subscribe()` never runs: **all
`connect()` subscriptions stay detached for the ViewModel's lifetime.** Probe
`InitRaiseSkipsSubscribeProbeTest` confirms this: `handled == 1` and `_emitter.subscriptionCount == 0`, while
the control case attaches 1 subscription. Plan 004's reorder (subscribe before init) fixes it only if
`subscribe()` moves out of `init`'s `safeExecute` block. This defect is not part of #266.

---

## 2. Goals and non-goals

**Goals**
1. A signal posted when **no collector that accepts it** is attached is not lost. It is held, and the first
   accepting collector to attach receives it **exactly once**. This covers `init`, rotation, and backgrounded
   windows (T1–T3).
2. Keep today's **fan-out** for live signals: every attached accepting collector receives a live signal (T4).
3. Work correctly with **several type-filtered `HandleSignal<T>` blocks** on one screen. That is the documented
   pattern: `docs/concepts.md:81` uses `HandleSignal<MySignal.ShowError>`, and the `LocalSignal.kt:34` KDoc uses
   `HandleSignal<CounterSignal.ShowError>`.
4. **No replay** of signals that were already delivered, so a navigation signal does not fire again after
   rotation.
5. Keep memory bounded and never suspend `post` forever when no collector ever attaches.
6. Kotlin/Native safe: no new unsupervised coroutines.
7. Make the contract explicit in KDoc and docs.

**Non-goals**
- Consumer-side conflation and handler cancellation (T5, T6). These are delegated to **plan 002** (§3.5).
- `Event`s emitted from `init`: **plan 004**.
- The "`init` raise skips `subscribe()`" defect (§1.8).
- The Swift sample's `@Published` conflation and iOS DX: **plan 019**.
- Exactly-once delivery across **process death**. Held signals live in the ViewModel's memory (plan 018 covers
  state restoration).
- Changing `anchor-test`'s `AnchorTestRuntime`. It records `post` calls directly and does not model delivery.
  Plan 006 documents runtime and test-runtime divergence.

---

## 3. Design

### 3.1 Options considered

| Option | `init` signal | Several typed `HandleSignal` | Live fan-out | Replay after rotation | API break | Verdict |
|---|---|---|---|---|---|---|
| (a) Channel, consume-once | delivered | **broken**: collectors steal each other's signals | **lost**: each signal goes to one collector | none | yes (`signals` type) | rejected |
| (b) Hold while `subscriptionCount == 0`, drain to first subscriber | delivered | **broken** for held signals: the first subscriber drains them all | kept | none | no | rejected as-is; refined into (b′) |
| **(b′) Hold until the first *accepting* collector attaches (type-aware)** | delivered | **works** | kept | none | **no** (type kept) | **recommended** |
| (c) Bounded replay plus consume-once dedup | delivered | works only if every consumer runs the dedup protocol | kept, with bookkeeping | **re-delivers** to collectors that don't dedup | semantic break for raw collectors | rejected |
| (d) Document only | dropped | n/a | kept | none | no | rejected as the fix; shipped as interim docs if (b′) is delayed |
| (e) Delay `init` until the first signal collector attaches | delivered | depends | kept | none | no | rejected |

**(a) Channel-backed consume-once**, for example `Channel(BUFFERED).receiveAsFlow()`, as the issue proposes.

```kotlin
private val channel = Channel<SignalProvider>(Channel.BUFFERED)
override val signals: Flow<SignalProvider> = channel.receiveAsFlow()
override suspend fun post(block: SignalScope.() -> Signal) { channel.send(SignalProvider { SignalScope.block() }) }
```

Rejected. A Channel hands each element to *one* receiver. Probe A1 composes `HandleSignal<Toast>` and then
`HandleSignal<Nav>` on the same flow: the Toast collector receives `Nav.Home`, filters it out, and Nav is lost
(`navHandled == []`). The same thing breaks every live signal on any screen that has two handlers. It also
changes `AnchorSink.signals` from `SharedFlow` to `Flow` (breaking).

**(b) Plain buffer-until-first-subscriber.**

```kotlin
suspend fun post(s: Signal) = lock.withLock {
  if (live.subscriptionCount.value == 0) pending.addLast(s) else live.emit(s)
}
val signals = live.onSubscription { lock.withLock { pending.toList().also { pending.clear() } }.forEach { emit(it) } }
```

It solves single-handler screens (probe B1). With two typed handlers, whichever one subscribes first drains
**all** held signals and drops the ones it does not accept. Probe B2: `Nav.Home` is drained by the Toast
subscriber and lost. For the same reason, "is anyone subscribed" is the wrong question to ask. The right
question is whether anyone who would *accept this signal* is subscribed.

**(b′) Recommended.** See §3.2. Prototype tests all pass, including a 200-round concurrency stress
(Appendix A/B).

**(c) Bounded replay plus consume-once dedup.** This is `replay = N` with a per-signal id or claimed flag. The
parked January branch `origin/fix-signal-handling-801299363541751028` (Jules task behind #152, never merged)
implements a variant: `replay = 1` plus `SignalEvent(signal, id)`. Its `HandleSignal` dedups with a
`var lastProcessedId` **local to each `LaunchedEffect`**. A new `HandleSignal` after rotation starts with
`lastProcessedId = null` and re-handles the replayed last signal, which is the "navigation fires again" bug.

A correct (c) needs a **shared** claimed flag that every consumer honours. Raw collectors of `AnchorSink.signals`
or `nativeSignals()` would see up to N stale signals on every re-subscription unless they implement the
protocol. It also keeps N signal objects in memory indefinitely. Rejected: it has more moving parts than (b′)
and pushes correctness onto every consumer.

**(d) Document only.** Add "signals posted while no collector is attached are dropped; anything the user must
see as a result of `init` belongs in state" to the `post`, `HandleSignal` and `ErrorScope` KDocs and to the docs.
Rejected as the resolution: it leaves a one-shot primitive that loses events on the most common screen path, and
it contradicts the library's own `ErrorScope` example. It is the fallback if the maintainer declines (b′)
(open question Q1).

**(e) Gate `init` on the first signal collector**, for example `_signals.subscriptionCount.first { it > 0 }`
before `consumeInitial()`. Rejected for three reasons: a screen without any `HandleSignal` never runs `init`;
headless and iOS-without-signals use cases hang; and it does nothing for T3 (background or rotation).

### 3.2 Recommended design (b′): hold until the first accepting collector

**Contract** (to go verbatim into KDoc):

> A posted signal is delivered to every collector that is attached and accepts it at the time of posting.
> If no attached collector accepts it, the signal is **held**, up to 64 signals, dropping the oldest, and is
> delivered **once** to the first accepting collector that attaches afterwards. Delivered signals are never
> replayed. A signal already handed to a collector that is cancelled before processing it is lost, as today.

**Components** (new `internal class SignalBus` in `anchor/src/commonMain/kotlin/dev/kioba/anchor/internal/SignalBus.kt`,
internal unless noted otherwise):

- `held: ArrayDeque<SignalProvider>`, bounded by `HELD_CAPACITY = 64` with drop-oldest. It is bounded because
  no collector may ever attach, and `post` must never suspend waiting for one.
- `raw: MutableSharedFlow<SignalProvider>(extraBufferCapacity = 64)` serves **accept-all** collectors:
  `AnchorSink.signals`, `ContainerViewModel.signals`, and `nativeSignals()`. The public type stays
  `SharedFlow<SignalProvider>` because `raw.asSharedFlow().onSubscription { ... }` returns a `SharedFlow`.
  Implementing `SharedFlow` ourselves is not an option: it is `@SubclassOptInRequired(ExperimentalForInheritanceCoroutinesApi::class)`
  (`SharedFlow.kt:123`).
- `typed: MutableSharedFlow<SignalProvider>(extraBufferCapacity = 64)` plus `typedAcceptors: MutableList<(Signal) -> Boolean>`
  serve **type-filtered** collectors (`HandleSignal<T>`), through
  `internal fun AnchorRuntime.signalsMatching(accepts)`. `anchor-compose` can see it only through a **public**
  `ContainerViewModel.signalsMatching(accepts: (Signal) -> Boolean): Flow<SignalProvider>`.
  - It has to be public because `anchor-compose` uses only the `anchor` module's public API. `@PublishedApi internal`
    does not cross module boundaries, and no opt-in marker exists yet.
  - Plan 007's `@InternalAnchorApi` would be the natural gate for it (Q7).
- One `Mutex` guards `held`, `typedAcceptors`, and the live-or-hold decision.

**Algorithm** (prototype in Appendix B):

- `post(p)`, under the lock:
  - if `raw.subscriptionCount > 0`, emit to `raw`;
  - if any typed acceptor accepts, emit to `typed`;
  - if neither happened, append to `held`.
- **Raw subscribe**: `raw.onSubscription { drain all of held under lock; emit each }`.
- **Typed subscribe**:
  ```kotlin
  flow {
    try {
      typed.onSubscription { under lock: register accepts; drain matching held }.filter(accepts).collect(::emit)
    } finally {
      withContext(NonCancellable) { lock.withLock { unregister } }
    }
  }
  ```

**Why this is race-free** (each point is backed by the kotlinx.coroutines docs or a prototype test):

1. `onSubscription` runs "*after the subscription is registered [...] It is guaranteed that all emissions to the
   upstream flow that happen inside or immediately after this `onSubscription` action will be collected by this
   subscription*" (`operators/Share.kt:423-431`). A post that sees the new acceptor, or `raw` count ≥ 1, emits
   into a slot that already exists.
2. A post that runs before registration or before the count increments puts the signal in `held`, under the
   same lock. The drain takes the lock afterwards, so it is included. A signal is either held or live, never
   both, so it cannot be delivered twice.
3. Drained, older signals are emitted by `onSubscription` before the subscriber reads its live buffer, which
   preserves order (prototype "held then live keep order").
4. Stress: 200 rounds × 200 concurrent posters racing one subscriber, on `Dispatchers.Default`, for both the raw
   and typed paths. No loss and no duplicates.
5. The design starts no coroutines of its own, so the Native abort path from §1.7 cannot be triggered.

**Known edges (documented, accepted):**

- A raw accept-all collector, such as an analytics collector on `ContainerViewModel.signals`, is a legitimate
  acceptor. It can therefore claim a held signal before a later typed `HandleSignal` attaches. The same applies
  to a live signal posted while only a raw collector is attached. See Q4.
- `emit` into a full live buffer suspends while the lock is held. Other posts are already back-pressured in
  that situation, but typed register and unregister calls wait too. There is no deadlock: the SharedFlow frees
  a cancelled subscriber's slot, which resumes the emitter, before our `finally` asks for the lock.
- Signal types that no `HandleSignal` handles now sit in `held` (at most 64) instead of being dropped. A
  handler that attaches later, for example in a conditionally composed branch, receives them late. See Q3.

### 3.3 API surface

| Symbol | Change | Breaking? |
|---|---|---|
| `AnchorSink.signals: SharedFlow<SignalProvider>` (`Anchor.kt:88`) | Same type. The first raw collector now receives held signals once. | **Non-breaking** at source and binary level. Behaviour change (an improvement). |
| `ContainerViewModel.signals: Flow<SignalProvider>` (`ContainerViewModel.kt:30-31`) | Delegates as before | Non-breaking |
| `nativeSignals(): NativeSharedFlow<SignalProvider>` (iOS) | Unchanged; it inherits the held semantics through `signals` | Non-breaking |
| `AnchorRuntime._signals` (`@PublishedApi internal`) | Removed and replaced by `signalBus` | Internal. The repo has no inline references (checked with grep). Binary-incompatible only for code compiled against the internal symbol. |
| `AnchorRuntime.signalsMatching(accepts)` | New `internal` | n/a |
| `ContainerViewModel.signalsMatching(accepts): Flow<SignalProvider>` | New **public** (needed across the module boundary; see §3.2) | **Additive** (non-breaking); grows the public surface (Q7) |
| `LocalSignals` (`@PublishedApi internal`, `LocalSignal.kt:19-21`) | Type changes from `Flow<SignalProvider>` to a `@PublishedApi internal fun interface SignalSource` in `anchor-compose` | Internal, but `HandleSignal`/`RememberAnchor` are `public inline`, so **client binaries compiled against 0.1.8 must be recompiled**. That is normal for a 0.x bump, and it is already true once plan 002 changes the `HandleSignal` body. |
| `HandleSignal<T>` | Collects `source.signalsMatching { it is T }` inside plan 002's loop | Signature unchanged |

### 3.4 iOS

There is no API change. `nativeSignals().collect { }` is a raw accept-all collector, so the first `collect`
drains anything held since construction. That covers both the sample's in-`init` collection and `.task {}`
collection. The Swift sample's `@Published` conflation remains plan 019's concern.

### 3.5 Consumer side: delegated to plan 002

Plan 002 rewrites `HandleSignal` to collect the stream directly inside
`LaunchedEffect { lifecycleOwner.repeatOnLifecycle(STARTED) { signals.collect { ... } } }`. That fixes T5 and T6.
This spec's evidence (probes H1 and H2) **confirms plan 002's premise** with the real `HandleSignal`. Integration
is a one-line change inside 002's loop: `signals.collect` becomes `source.signalsMatching { it is T }.collect`.

With 002's `repeatOnLifecycle`, a stopped app detaches its collectors, so signals posted in the background are
**held and delivered on return** instead of dropped (T3). Plan 002 lists this in its maintenance notes as "*an
improvement, but confirm it's wanted*" (Q2). Plan 002's step-2 KDoc line "*Known limitation: signals posted
before the first composition collects [...] are not delivered*" must be removed when this lands.

---

## 4. Behaviour changes

| Scenario | Before | After |
|---|---|---|
| `post` from `init`, UI attaches later | dropped | delivered once to the first accepting handler |
| `post` during rotation (old composition gone, new one not attached) | dropped | delivered once to the new composition's accepting handler |
| `post` while backgrounded (below `STARTED`) | dropped | delivered on return (with plan 002) |
| `post` with no handler for that type, ever | dropped | held (at most 64, oldest dropped); memory bounded |
| Live `post` with 2 accepting handlers | both | both (unchanged) |
| New collector after delivery | nothing | nothing (no replay; unchanged) |
| Burst or slow handler (T5, T6) | conflated or cancelled | fixed by plan 002, not by this spec |

---

## 5. Acceptance criteria

1. The issue's `InitSignalDeliveryTest` passes verbatim.
2. A `ContainerViewModel`-level test passes: `init = { post { X } }`, collector attaches 16 ms later, receives X.
3. `SignalBusTest` passes. It covers:
   - late raw collector gets a held signal once, with no replay;
   - two typed handlers each receive their own held signal;
   - an unmatched typed signal stays held while another typed handler is live;
   - live fan-out to raw and typed collectors;
   - held-then-live order;
   - bound and drop-oldest;
   - the concurrency stress (200×200, raw and typed).
4. If plan 001's harness exists, a Compose test passes: `HandleSignal<Nav>` and `HandleSignal<Toast>` on one
   screen each receive a signal posted from `init`.
5. `./gradlew :anchor:iosSimulatorArm64Test` passes, which runs the common tests on Native (no signal-6 abort).
6. The KDoc on `post`, `AnchorSink.signals`, `HandleSignal`, and `nativeSignals()` states the §3.2 contract.
   `docs/concepts.md` and `docs/compose.md` do too, and `docs/llms-full.txt` is regenerated.
7. `./gradlew build` exits 0.

---

## 6. Open questions for the maintainer

- **Q1 (gating). Is multi-`HandleSignal` fan-out a contract or an accident?**
  - Evidence that it is a contract: `HandleSignal<T>` is reified and type-filtered, the docs show a narrow
    subtype handler (`docs/concepts.md:81`), and today every attached collector sees every live signal (probe P2).
  - If it is a **contract**, (b′) as specified.
  - If it is an **accident**, the maintainer may prefer a single-consumer rule: (b′) with consume-once for live
    signals too, or `RememberAnchor` as the sole collector dispatching to registered handlers. Held-signal
    routing still has to be type-aware (probes A1 and B2 show why).
  - If neither: (d), docs only.
- **Q2.** Should signals posted while the app is backgrounded be **delivered on return** (the (b′) + plan 002
  default) or dropped as today? If dropped, `HandleSignal` would have to stay attached below `STARTED`, which
  plan 002 changes.
- **Q3.** Should held signals **expire**, for example with a TTL or "held only until the first frame"? Or is
  "held until an accepting collector attaches, at most 64, drop-oldest" acceptable?
- **Q4.** Should raw `AnchorSink.signals` / `ContainerViewModel.signals` collectors count as acceptors and
  claim held signals, as specified? Or should only `HandleSignal` and `nativeSignals()` claim them?
- **Q5.** Is 64 the right held capacity, and should it be configurable in `create(...)`? The spec keeps it
  internal and fixed.
- **Q6.** Should this ship in 0.1.9 (unreleased, currently on master) or in 0.2.0? No existing public
  signature changes, but `@PublishedApi` inline bodies change, so clients must recompile.
- **Q7.** Is a new public `ContainerViewModel.signalsMatching(accepts)` acceptable?
  - Alternatives: land plan 007's `@InternalAnchorApi` opt-in first and annotate it, or move `HandleSignal`'s
    runtime link into the `anchor` module.
  - Plan 020 is deciding how much public surface to commit to before 1.0.

---

## 7. Relationship to existing plans

| Plan | Relationship |
|---|---|
| **017** (signal semantics design spike) | **Superseded.** This spec delivers 017's §1 (truth table, §1.3), §2 (options with sketches, §3.1), §3 (parked-branch verdict: it implements a per-collector-dedup variant of (c) that re-delivers after rotation, so **delete it; nothing to mine**) and §4 (recommendation plus the fan-out question). It also includes the prototype that 017's test plan asked for (Appendix B). |
| **002** (HandleSignal lossless delivery) | **Depends on.** Consumer conflation and cancellation are delegated to 002. The plan's Compose step edits 002's new loop. This spec's probes H1 and H2 confirm 002's premise. |
| **001** (anchor-compose test harness) | **Depends on**, through 002. Appendix C documents a lighter headless harness (no `compose.uiTest`/skiko) that worked on `0bc430c`, as a fallback if 001's `runComposeUiTest` route hits its STOP conditions. |
| **004** (init event delivery) | **Complements.** It is the same "emitted from `init`" defect for `Event`s, with a different fix (ordering). Both edit `AnchorRuntime.kt`, so a trivial rebase is needed. 004 is stale: it assumes plan 003's `handlerFlows()`, while live code has `handlers()` plus SupervisorJob from PR #239. Also see §1.8. |
| **019** (iOS DX parity) | Complements: owns the Swift sample's `@Published` conflation. |
| **006** (anchor-test fidelity) | Complements: its divergence KDocs should note that `AnchorTestRuntime` records `post` calls and does not model delivery timing. |
| #140 (docs; another verifier) | The guarantees section in the docs should quote §3.2's contract. This plan adds the text to `docs/concepts.md`/`docs/compose.md`; #140 owns any broader threading/guarantees page. |

---

## Appendix A: probe inventory (all run on `0bc430c`, desktop JVM)

| Probe | File (scratch only) | Asserts | Result |
|---|---|---|---|
| Reproducer | `anchor/.../InitSignalDeliveryTest.kt` (verbatim from issue) | init signal received | **FAILED** (bug) |
| P1 | `SignalSemanticsProbeTest` | `ContainerViewModel`, collector +16 ms → null | pass (drop confirmed) |
| P2 | same | 2 collectors both receive | pass |
| P3 | same | latest-value holder conflates a 3-burst | pass |
| A1 | same | Channel: typed collectors steal each other's signals | pass (defect of (a)) |
| B1, B2 | same | plain (b): single OK; typed pair loses `Nav` | pass (defect of (b)) |
| T1–T6 | same | single-path typed prototype | pass |
| Bus ×7 | `SignalBusPrototypeTest` | recommended dual-path (b′) incl. 200×200 stress | pass |
| H0–H4 | `anchor-compose/src/desktopTest/.../HandleSignalConflationProbeTest.kt` | real `HandleSignal`: baseline, burst conflation, slow-handler cancel, pre-first-frame drop, below-STARTED drop | pass (×3 reruns) |
| §1.8 | `InitRaiseSkipsSubscribeProbeTest` | handled init raise ⇒ `_emitter.subscriptionCount == 0` | pass (adjacent defect) |

Commands: `./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.*Probe*'`,
`./gradlew :anchor:desktopTest --tests 'dev.kioba.anchor.SignalBusPrototypeTest'`,
`./gradlew :anchor-compose:desktopTest`. The last one needed a scratch-only
`desktopTest { dependencies { implementation(libs.kotlin.coroutinesTest) } }` in `anchor-compose/build.gradle.kts`.

## Appendix B: prototype of (b′) (passed all 7 tests)

```kotlin
private class SignalBusPrototype(private val capacity: Int = 64) {
  private val lock = Mutex()
  private val held = ArrayDeque<Signal>()
  private val typedAcceptors = mutableListOf<(Signal) -> Boolean>()
  private val raw = MutableSharedFlow<Signal>(extraBufferCapacity = capacity)
  private val typed = MutableSharedFlow<Signal>(extraBufferCapacity = capacity)

  suspend fun post(signal: Signal) {
    lock.withLock {
      var delivered = false
      if (raw.subscriptionCount.value > 0) { raw.emit(signal); delivered = true }
      if (typedAcceptors.any { it(signal) }) { typed.emit(signal); delivered = true }
      if (!delivered) {
        if (held.size == capacity) held.removeFirst()
        held.addLast(signal)
      }
    }
  }

  val signals: SharedFlow<Signal> =
    raw.asSharedFlow().onSubscription {
      val drained = lock.withLock { held.toList().also { held.clear() } }
      drained.forEach { emit(it) }
    }

  fun signals(accepts: (Signal) -> Boolean): Flow<Signal> =
    flow {
      try {
        typed
          .onSubscription {
            val drained =
              lock.withLock {
                typedAcceptors += accepts
                val (match, rest) = held.partition(accepts)
                held.clear()
                held.addAll(rest)
                match
              }
            drained.forEach { emit(it) }
          }.filter(accepts)
          .collect { emit(it) }
      } finally {
        withContext(NonCancellable) { lock.withLock { typedAcceptors.remove(accepts) } }
      }
    }
}
```

Production code carries `SignalProvider` rather than `Signal`. It filters on `provider.provide()`, which is safe
because `post` evaluates the signal eagerly (`AnchorRuntime.kt:225`).

## Appendix C: headless Compose harness (worked on `0bc430c`)

```kotlin
val main = newSingleThreadContext("test-main"); Dispatchers.setMain(main)   // repeatOnLifecycle needs Main
runBlocking(main) {
  val clock = BroadcastFrameClock()
  val recomposer = Recomposer(coroutineContext + clock)
  launch(clock) { recomposer.runRecomposeAndApplyChanges() }
  val composition = Composition(UnitApplier(), recomposer)          // AbstractApplier<Unit> no-op
  val owner = TestOwner().apply { registry.currentState = RESUMED }  // LifecycleRegistry.createUnsafe(this)
  composition.setContent {
    CompositionLocalProvider(LocalLifecycleOwner provides owner, LocalSignals provides signals) { /* HandleSignal */ }
  }
  suspend fun pump() = repeat(5) { Snapshot.sendApplyNotifications(); clock.sendFrame(System.nanoTime()); delay(10) }
}
```

The only extra dependency is `kotlinx-coroutines-test` in `desktopTest`. It needs no skiko/UI and no
`compose.uiTest`.
