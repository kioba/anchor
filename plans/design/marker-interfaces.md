# Design note: the marker interfaces (`ViewState`, `Effect`, `Signal`, `Event`) (#133)

- **Plan**: `plans/020-marker-interfaces-design-note.md` (design only, no code change)
- **Written**: 2026-09-29, against origin/master `0bc430c` and the open PR heads listed in §2.1
- **Status**: at the decision gate. The maintainer decides, then runs the tracker actions and
  any code follow-up (Appendix B has the drafts).
- **Quotable by**: #140 / plan 028 (docs overhaul). §1.6 is the "what the markers guarantee" table.

## TL;DR

**Recommendation: keep all four markers as generic bounds (Option 1), and do the documentation
half of Option 3.** Rewrite each marker's KDoc so it states what the bound guarantees and what its
channel delivers. Add no library sub-markers and no behavior.

Why, in one paragraph: the markers are the only thing that keeps State, Signal and Event apart at
compile time. Without them, eight realistic mistakes compile silently (§1.5). Examples: an `Event`
posted as a UI signal, `HandleSignal<SomeEvent>`, `connect<SomeSignal>`, or a `String` used as
state. The open PRs move runtime routing onto exactly these types. #271 dispatches `anchor()` by
`S::class`, and #277 hands held signals to the `HandleSignal<T>` whose `T` matches, so a mistake
that used to be merely odd now fails silently at runtime. Removal would also break the ABI on JVM
and iOS just as plan 029 starts pinning it. It would force changes in #271, #277 (including an API
that PR makes public for the first time) and #279. It saves one supertype per declared type, which
is 9 declarations across the 3 sample features. `Effect` is the weakest of the four; §3 D2 asks
whether to keep it for symmetry (recommended) or relax it on its own.

## 0. Drift check and corrections to the plan

- `git show origin/master:anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt`: the four markers
  and their companions are unchanged. `ViewState` is at `Anchor.kt:20`, `Effect` at `:27`,
  `EmptyEffect` at `:32`, `Signal` at `:39`, `UnitSignal` at `:44`, `Event` at `:51` and
  `Created` at `:56`.
- #133 still has no design note. It was closed COMPLETED on 2026-06-02T23:11:32Z, has 0 comments
  and no linked change (`gh issue view 133 --json comments,stateReason,closedAt`). The plan's STOP
  condition does not trigger.
- **Plan text that drifted**:
  - `listen<reified A : Event>` is now `connect<reified A>() where A : Event`, at
    `SubscriptionDsl.kt:68-74`. It was renamed in `edc1ee3` (#239).
  - The plan cites "pending PR #242/#169" as evidence that the "enrich" direction is being walked.
    That is stale. PR #242 (`NavigationSignal : Signal`, with `Back` and `BackWith<T>`) was
    **closed unmerged** on 2026-06-03 with 0 comments. #169 was closed COMPLETED with 0 comments,
    and `git grep NavigationSignal origin/master` finds nothing. The direction was tried once and
    dropped (see also `plans/specs/027-release-0-1-9-migration.md:29`).
- **#133's own claims that are stale**:
  - "The marker interfaces in `AnchorMarkers.kt`": that file was merged into `Anchor.kt` by
    `1ee66f2` (#152).
  - "Provide no documentation (no kdoc)": KDoc was added by `11ab61c` (#153, 2026-01-26), a month
    after #133 was filed (2025-12-26).

## 1. What the markers do, and what they don't

### 1.1 Inventory

| Marker | Declared | Companion | Where the library consumes it (master) |
|---|---|---|---|
| `ViewState` | `Anchor.kt:20` | none | bound `S : ViewState` on every capability type (`Anchor.kt:78,128,137,161,210`, `BaseAnchorScope.kt:21`, `AnchorScope.kt:27`, `RememberAnchorScope.kt:43`). `RememberAnchor` keys the ViewModel by `S::class.qualifiedName` (`anchor-compose/.../RememberAnchor.kt:155`). |
| `Effect` | `Anchor.kt:27` | `object EmptyEffect : Effect` (`:32`) | bound `R : Effect` in the same places; `effect { }` runs with `R` as receiver (`Anchor.kt:196-199`) |
| `Signal` | `Anchor.kt:39` | `object UnitSignal : Signal` (`:44`) | `post(block: SignalScope.() -> Signal)` (`Anchor.kt:280-282`), `SignalProvider.provide(): Signal` (`:65`), `HandleSignal<reified T : Signal>` filters with `signal is T` (`LocalSignal.kt:41,48`) |
| `Event` | `Anchor.kt:51` | `object Created : Event` (`:56`) | `emit(block: SubscriptionScope.() -> Event)` (`Anchor.kt:254-256`), the bus is `MutableSharedFlow<Event>` (`AnchorRuntime.kt:64`), `connect<reified A : Event>` filters with `filterIsInstance()` (`SubscriptionDsl.kt:68-72`), `Created` is emitted on subscription (`AnchorRuntime.kt:88`) |

### 1.2 Role A: generic bounds everywhere

These are measured numbers; §2.1 has the per-PR breakdown. Across the library's main sources
(`anchor`, `anchor-compose`, `anchor-test`, `*/src/*Main`) on master:

- **95 bound sites** (`X : Marker`): ViewState 48, Effect 44, Event 2, Signal 1.
- **18 direct type uses**, such as `-> Signal`, `Flow<Event>` and `provide(): Signal`.
- **4 `out Effect`/`out ViewState` projections** (`AnchorAction.kt:42,85,129,158`).
- **39 public declarations** carry a marker in their signature (enumerated in Appendix A.2), plus
  the `PureAnchor` and `ErrorScope` typealiases, which inherit them.

The bounds add **no runtime behavior**. No library code calls a method on a marker (they have
none), and no code runs `is ViewState` or `is Effect`. The only runtime type tests are
`signal is T` (`LocalSignal.kt:48`) and `filterIsInstance<A>()` (`SubscriptionDsl.kt:72`), both
on the *consumer's* subtype. A bound's whole value is what it rejects at compile time (§1.5).

### 1.3 Role B: type-driven routing (master, plus what the open PRs add)

| Routing key | Where | What the marker contributes |
|---|---|---|
| `HandleSignal<T>` filters `signal is T` | `LocalSignal.kt:41-51` | `T : Signal` rejects state and event types (M3, M5). Without it, the handler compiles and never fires. |
| `connect<A>` filters `filterIsInstance<A>()` | `SubscriptionDsl.kt:68-74` | `A : Event` rejects signal types (M4) |
| ViewModel key `S::class.qualifiedName` | `RememberAnchor.kt:155`; `customKey` exists "when you need multiple instances of the same state type" (`:119-120`) | `S : ViewState` means a `String`, `Int` or `List<…>` can never be a state type (M6), so two unrelated screens cannot share a key through a stdlib type |
| **#271**: `LocalAnchors: Map<KClass<*>, AnchorScope<*, *>>`; `anchor()` resolves `LocalAnchors.current[S::class]` | #271 `LocalScope.kt`, `AnchorAction.kt` (all four overloads are `inline fun <R : Effect, reified S : ViewState, Err : Any>`) | Action routing now **depends** on each anchor having its own nominal state class. A stdlib state type would route `anchor(X::action)` to whichever enclosing anchor shares it ("nearest wins", per #271's `docs/compose.md`), and the unchecked cast `(this as Anchor<R, S, Err>)` would not catch it (erased). The marker excludes the stdlib case at compile time. |
| **#277**: held signals routed by `signalsMatching { it is T }` | #277 `LocalSignal.kt`, `SignalBus.kt`, and a new public `ContainerViewModel.signalsMatching(accepts: (Signal) -> Boolean)` | A signal that no attached collector accepts is **held** (up to 64, oldest dropped). An event posted by mistake (M1) no longer just gets filtered out. It sits in the held queue, reaches the first accept-all collector (`nativeSignals()`, `AnchorSink.signals`), or is dropped. The `Signal` bound on `post` is what makes M1 a compile error. |
| **#279**: `AnchorContainer<E, S>` exposes a typed `state: S` to Swift | #279 `AnchorContainer.kt` (`where E : Effect, S : ViewState`) | The class generics reach Swift as lightweight generics. The sample constrains them `where E: Effect, S: ViewState` (`iosApp/iosApp/ViewModelProtocol.swift:24,61,71`). |

### 1.4 Role C: companion defaults, and Role D: the iOS export

- **Companions** are instances that satisfy the bounds.
  - `EmptyEffect` is the documented "no dependencies" scope (`README.md:47,50`, `docs/index.md:47,50`,
    `docs/compose.md:163,166`, and 30+ test fixtures).
  - `UnitSignal` is the Swift sample's "no signal yet" placeholder
    (`iosApp/iosApp/ViewModelProtocol.swift:42`, `CounterView.swift:87`).
  - `Created` is a lifecycle event that consumers `connect<Created>` to (`docs/concepts.md:88-92`).
  - None of them needs a marker to *exist*. They need one only to pass the bounds.
- **iOS export** (not in the plan's list, but it is a real consumer surface):
  - Kotlin/Native exports the markers as ObjC protocols `ViewState`, `Effect`, `Signal` and
    `Event`. Members bounded by them erase to the protocol type. Examples:
    `@property (readonly) id<AnchorViewState> state`,
    `- (AnchorNativeStateFlow<id<AnchorViewState>> *)nativeViewState`, and `create` taking
    `id<AnchorEffect> (^)(void)` (local build header
    `anchor/build/bin/iosSimulatorArm64/debugFramework/anchor.framework/Headers/anchor.h:168,839,976-977,1060`,
    untracked output dated 2026-07-07).
  - Under `: Any` bounds these become `id`.
  - The Swift sample relies on the protocols: `func provide() -> any Signal`,
    `where E: Effect, S: ViewState`, and
    `as! shared.Anchor<any Effect, any ViewState, AnyObject>` (`ViewModelProtocol.swift:19,24,35`).
  - Plan 019's proposed Swift helper `func handleSignal<T: Signal>(…)`
    (`plans/019-ios-dx-parity-design.md:115`) assumes the protocol exists.

### 1.5 Evidence: what the bounds reject

This is a standalone prototype compiled with Kotlin **2.4.0**, the repo's version
(`gradle/libs.versions.toml:15`), using `kotlin-compiler-embeddable` from the local Gradle cache.
Variant A mirrors master's shapes (markers as bounds). Variant B keeps the interfaces but relaxes
every bound to `Any`. The same consumer file is compiled against both; the full source is in
Appendix A.1.

| # | Consumer mistake | A: markers | B: `: Any` |
|---|---|---|---|
| M1 | `post { CounterEvent.Refresh }`: an Event on the Signal channel | `error: return type mismatch: expected 'Signal', actual 'CounterEvent.Refresh'` | compiles |
| M2 | `emit { CounterSignal.Inc }`: a Signal on the Event bus | `error: … expected 'Event', actual 'CounterSignal.Inc'` | compiles |
| M3 | `handleSignal<CounterEvent> { }` | `error: type argument is not within its bounds … must be subtype of 'Signal'` | compiles, never fires |
| M4 | `connect<CounterSignal> { }` | `error: … must be subtype of 'Event'` | compiles, never fires |
| M5 | `handleSignal<String> { }` | `error: … must be subtype of 'Signal'` | compiles, never fires |
| M6 | `create<EmptyEffect, Int, Nothing>(…)`: primitive state | `error: … 'S' … must be subtype of 'ViewState', but actual: 'Int'` | compiles (and collides in `S::class`-keyed maps) |
| M7 | state class that forgot `: ViewState` | `error: … must be subtype of 'ViewState', but actual: 'PlainState'` | compiles |
| M8 | `effectScope = { Repository() }` where the class is not `: Effect` | `error: … must be subtype of 'Effect', but actual: 'Repository'` | compiles (**and this one is a feature**: B lets you pass a dependency holder you don't own) |

Variant A exits 1 with 17 `error:` lines; variant B exits 0 with no diagnostics.

**DX cost that shows up here**: for each bound violation, K2 prints a second, confusing diagnostic.
Examples: `argument type mismatch: actual type is '() -> EmptyEffect', but '() -> EmptyEffect' was expected`,
and `'(CounterEvent) -> Unit', but '(CounterEvent) -> Unit' was expected`. A user who forgot
`: ViewState` sees two errors, and one of them looks nonsensical.

`create()` inference itself does not depend on the bounds. `R` and `S` are inferred from the
`effectScope`/`initialState` lambdas in both variants. The bound only rejects; it never steers
inference.

### 1.6 What the markers guarantee, and what they don't (quotable)

| They DO | They DON'T |
|---|---|
| Keep the three channels apart at compile time. A type must opt into `Signal` to be posted or handled, and into `Event` to be emitted or `connect`ed (M1-M5). | Add behavior. They have no members, and nothing checks them at runtime. |
| Force a dedicated, nominal state class, which is what the `S::class`-keyed ViewModel store (master) and `anchor()` routing (#271) assume (M6, M7). | Guarantee one state class per anchor. Two anchors can share a `ViewState` class (hence `customKey`), and a generic `data class Loadable<T>(…) : ViewState` erases to one `KClass` for every `T`. |
| Give Swift named protocols in the framework header instead of `id` (§1.4). | Stop one type from implementing both `Signal` and `Event`, which defeats the channel split. |
| Document intent in every signature (`Anchor<CounterEffect, CounterState, Nothing>`). | Tell one feature's signals from another's: `HandleSignal<OtherFeatureSignal>` compiles. |
| | Give exhaustiveness. Sealing is the consumer's choice (`sealed interface CounterSignal : Signal`). |
| | Help with types you don't own. A third-party or generated class can't be a state or effect without a wrapper (M8). |

### 1.7 Per-marker verdict

| Marker | Value | Cost to consumers | Verdict |
|---|---|---|---|
| `ViewState` | High. Nominal state type for the KClass-keyed store and routing; M6/M7. | `: ViewState` once per state class | keep |
| `Signal` | High. Channel separation (M1, M3, M5); #277's type-routed held queue depends on it. | once per signal root (usually one sealed interface per feature) | keep |
| `Event` | Medium-high. Channel separation (M2, M4). | once per event root | keep |
| `Effect` | **Low.** No channel to confuse it with and no routing. It only rejects non-`Effect` holders (M8), which is arguably a cost, and it is why `EmptyEffect` exists. | once per feature, plus `EmptyEffect` for effect-less anchors | keep for symmetry (recommended) or relax alone (§2 Option 2′, D2) |

Consumer ceremony, measured: **9 marker supertype declarations across the 3 sample features**
(4 `ViewState`, 3 `Effect`, 1 `Signal`, 1 `Event`), and 46 across the whole repo including test
fixtures.

## 2. Options, with migration math

### 2.1 Blast radius: the census, per ref

Methodology:
- The plan's own census command (`grep -rn ": ViewState\|: Effect\|: Signal\|: Event\|where R : Effect\|S : ViewState" … | wc -l`)
  gives **99** on master (66 main, 33 test). It is noisy: it counts
  `block: SignalScope.() -> Signal` as `: Signal` 3 times.
- The table below uses a stricter census over the library's main sources, read with `git show`
  and no checkout (script in Appendix A.3).

| Ref | Adds | Bound sites | Direct type uses | `out` projections |
|---|---|---|---|---|
| origin/master `0bc430c` | none | **95** | 18 | 4 |
| #271 head `2651e8f` | `anchor()` ×4 become `<R : Effect, reified S : ViewState, Err : Any>`; `PreviewAnchor` gains a reified `S`; a no-op `AnchorScope<Effect, ViewState>` | 103 (+8) | 19 (+1) | 0 (−4) |
| #277 head `cd2a799` (on top of #271→#274→#275, with #272/#273 merged in) | `(Signal) -> Boolean` in `SignalSource`, `SignalBus` ×2, `AnchorRuntime.signalsMatching`, and the **new public** `ContainerViewModel.signalsMatching` | 103 | 24 (+5) | 0 |
| #279 head `a88f287` (on #277) | `AnchorContainer<E : Effect, S : ViewState>`, `createAnchor<S, E>` | 107 (+4) | 24 | 0 |
| #280 head `50c38b3` (on #273) | `anchorErrors()` has no marker in its signature | 95 (±0) | 18 | 4 |

**ABI exposure** measured today, before plan 029 commits dumps:
- **JVM**: `javap -s` on the local desktop classes (untracked build output, 2026-07-07). Public and
  protected members whose descriptor names a marker: `anchor` 7, `anchor-compose` 3,
  `anchor-test` 10. Most generics erase through `Function*` types, so JVM exposure is modest.
  The visible ones:
  - `StateAnchor.getState()` has descriptor `()Ldev/kioba/anchor/ViewState;`
  - `SignalProvider.provide()` has descriptor `()Ldev/kioba/anchor/Signal;`
  - `SubscriptionsScope.<init>(…Ldev/kioba/anchor/Effect;…)` and `getEffect()Ldev/kioba/anchor/Effect;`
  - `PreviewAnchor(ViewState, …)` and `AnchorStateScope.getState(Composer, int)`
  - The prototype confirms relaxing to `Any` turns them into `()Ljava/lang/Object;`
    (Appendix A.1).
- **iOS klib**: `~/.konan/kotlin-native-prebuilt-macos-aarch64-2.4.0/bin/klib dump-abi` on the local
  `anchor-iosSimulatorArm64Main.klib` (2026-07-06). 41 of 194 lines mention a marker.
  **13 function linkage signatures encode a marker bound**, for example
  `create(…){0§<dev.kioba.anchor.Effect>;1§<dev.kioba.anchor.ViewState>;2§<kotlin.Any>}`. So
  changing a bound changes the symbol's identity, not only the dump text. The 13 are:
  `RememberAnchorScope.create`, `SubscriptionsScope.connect`, `nativeViewState`,
  `nativeSignals`, `recover`, `rememberAnchor`, `catchDefects`, `catchDomainError`,
  `safeExecute`, `anchorContainerViewModelFactory`, `containerViewModelFactory`, and the
  `AnchorScope` factory and `execute` (plan 029 B6 deletes the last two).
- **After plan 029** (GO 2026-09-29), `anchor/api/desktop/anchor.api` and `anchor/api/anchor.klib.api`
  are committed and `checkKotlinAbi` runs in `./gradlew build`
  (`plans/029-file-organization.md:62-67,180-181`). Any bound change then fails the build until the
  dumps are regenerated, which makes the break reviewable.

### Option 1: keep as-is and document the contract

- **Change**: KDoc only, on `Anchor.kt:15-56`. Each marker states:
  - what the bound guarantees (§1.6),
  - its channel's delivery contract: State is conflated latest value; Signal has #277's
    held-once semantics; Event has #272/#273's bus semantics, with "dropped when no handler is
    attached" per #277's `emit` KDoc,
  - and for `ViewState`, the "one dedicated state class per `RememberAnchor`, otherwise
    `customKey`" rule that #271 relies on.
  - `docs/concepts.md:20,32` gain one sentence each.
- **Migration cost**: zero. There is no ABI diff: KDoc is not in `.api` or `.klib.api` dumps.
- **Cost**: the ceremony stays, at one supertype per type, forever after 1.0.
- **PR impact**: none of #271, #277, #279 or #280 must change. The KDoc PR touches `Anchor.kt`,
  which #277 also edits. Its KDoc lands on `post`/`emit` (lines 250+) and on `AnchorSink.signals`,
  while the markers sit at 15-56, so a textual conflict is unlikely. Land it after #277 anyway so
  it can cite the final contract.

### Option 2: remove the bounds

Two sub-variants. Both must relax to **`Any`**, not remove the bound entirely:

- `NativeStateFlow<T : Any>` (`anchor/src/iosMain/.../NativeFlows.kt:26`) cannot wrap a
  `StateFlow<S>` whose `S` is unbounded. The prototype's variant D fails with
  `type argument is not within its bounds: … must be subtype of 'Any', but actual: 'S'`.
- `S::class` itself compiles with an unbounded reified `S` (prototype variant C, exit 0), so #271
  would survive either way.

**2a. Relax to `Any` and keep the interfaces, `@Deprecated`, for one release.**
- **Signature changes**: all 95 bound sites plus #271's 8 and #279's 4. The 18 direct uses (+6 in
  the PRs) become `Any`: `provide(): Any`, `post(() -> Any)`, `emit(() -> Any)`, `Flow<Any>`,
  `signalsMatching((Any) -> Boolean)`. The 4 projections become `out Any`. 39 public
  declarations change.
- **Source**: consumer classes that still say `: ViewState` keep compiling, with a deprecation
  warning. Warnings-as-errors builds break. Code that uses a marker *as a type* breaks, for
  example `val s: Signal = provider.provide()`. The Swift sample's `func provide() -> any Signal`
  (`ViewModelProtocol.swift:19`) would no longer match the protocol requirement once the ObjC
  signature becomes `id`. That last point is inferred from the header shape and was not compiled.
- **Binary**: the JVM descriptors listed in §2.1, the 13 or more klib linkage signatures in `anchor`
  alone, and the ObjC header (`id<AnchorViewState>` becomes `id`). Every consumer has to recompile.
- **Companions**: `Created` becomes a plain `object Created` (`connect<Created>` still works).
  `UnitSignal` becomes a plain `object`. `EmptyEffect` becomes redundant (`effectScope = { Unit }`)
  and is kept deprecated.
- **Loses**: M1-M7 (§1.5) and the nominal-state guard that #271's routing leans on. #271 would need
  a KDoc warning instead: "S must be a dedicated class".
- **Gains**: M8. Types the consumer doesn't own can serve as state or effect. The generics match
  Orbit (`OrbitContainerHost<INTERNAL_STATE : Any, EXTERNAL_STATE : Any, SIDE_EFFECT : Any>`,
  `orbit-core/.../ContainerHost.kt:42`) and MVIKotlin
  (`Store<in Intent : Any, out State : Any, out Label : Any>`, `mvikotlin/.../Store.kt:94`),
  both upstream main/master as fetched 2026-09-29, which eases the migration docs #140 wants.

**2b. Delete the interfaces as well** (the literal #133 ask). This is everything in 2a plus a
source break for **every consumer type declaration**:
- 9 in the samples, 46 in the repo;
- the Swift sample's generic constraints and casts (`ViewModelProtocol.swift:24,35,61,71`);
- 6 user docs (`README.md`, `docs/{index,concepts,compose,examples,errors}.md`);
- a sweep of 17 plan and spec files (count as of 2026-09-29) that quote `: ViewState`, `: Signal` and so on (the plan's
  maintenance note).
- **"Breaks every consumer" applies here in full.**

### Option 2′: relax `Effect` only

`R : Effect` becomes `R : Any`; `ViewState`, `Signal` and `Event` stay.
- **Scope**: 44 of the 95 bound sites, plus #271's 4 and #279's 2. JVM: `SubscriptionsScope`'s
  `<init>` and `getEffect()`, and `anchor-test`'s effect-scope accessors. klib: 12 of the 13
  signatures carry an `Effect` bound, so the iOS binary break is almost as wide as full removal.
- **Gains**: M8 only, and `EmptyEffect` can retire.
- **Loses**: nothing that §1.5 protects.
- **Framing**: this is also the cleanest way to settle #138 ("Effect scope is half-baked"), which
  was closed on 2026-05-06 as "Review on example code, nothin to be done here".

### Option 3: enrich instead of remove

- **3a. Contract documentation.** This is Option 1's KDoc and is recommended.
- **3b. Library sub-markers**, such as `NavigationSignal : Signal`. This is additive and adds ABI
  without breaking any. It was tried in #242 and closed unmerged without comment, so the
  direction is declined in practice. Revisit only with a concrete second consumer; plan 019's
  Swift layer does not need it.
- **3c. Behavior on the markers**, such as a `Signal.key` for dedupe or a `ViewState` save hook for
  plan 018. Abstract members break every consumer, like 2b. Members with defaults add API with no
  current use case. Plan 018 already prefers an opt-in `saver` parameter on `create()`
  (`plans/018-state-restoration-spike.md:117`). Not recommended.

### 2.2 Which open PRs would have to change

| PR | Option 1 (keep + KDoc) | 2a (relax to `Any`, deprecate) | 2b (delete) | 2′ (`Effect` only) |
|---|---|---|---|---|
| **#271** `anchor()` by ViewState | none (its `docs/compose.md` "Nested anchors" text is consistent with the new KDoc) | 4 `anchor()` overloads and `PreviewAnchor` to `R : Any, reified S : Any`; the no-op `AnchorScope<Effect, ViewState>`; KDoc must warn that `S` needs a dedicated class | 2a plus `NestedAnchorTest` fixtures (`: ViewState`, `GoHome : Signal`) and docs snippets | 4 overloads to `R : Any` |
| **#272 / #273** event bus / init order | none | none | tests' `connect<Event>` to `connect<Any>` (`EventBusReentrancyTest`, `InitEventDeliveryTest`) | none |
| **#274** compose test harness | none | none | fixture `: ViewState`, `TestSignal : Signal` | none |
| **#275 / #276 / #278** | none | none (the `HandleSignal` bound is master code, changed in the removal PR) | none | none |
| **#277** held signals | none | `SignalSource`, `SignalBus`, `AnchorRuntime.signalsMatching` and the **new public** `ContainerViewModel.signalsMatching(accepts: (Signal) -> Boolean)` to `(Any) -> Boolean`. **Decide before #277 merges**, or that public API ships and then breaks. | 2a plus test fixtures | none |
| **#279** iOS `AnchorContainer` | none | `AnchorContainer`/`createAnchor` where-clauses; the Swift sample's `where E: Effect, S: ViewState` and `as! shared.Anchor<any Effect, any ViewState, AnyObject>` (#279's `ViewModelProtocol.swift`) | 2a plus `AnchorContainerTest` fixtures | `E : Any`; Swift `E: Effect` to `E: AnyObject` |
| **#280** `anchorErrors()` | none | none: `anchorErrors` names no marker, and `SubscriptionsScope`'s class bounds are master code | fixtures `FetchEvent : Event`, `LoadEvent : Event`, `connect<Event>` | none |
| **Plan 029** ABI dumps | none | regenerate dumps; the diff is the break list | same | same |
| **Plan 027** 0.1.9 release notes | none | would have to add a "breaking" entry | same | same |

## 3. Recommendation

**Keep all four markers as bounds (Option 1), and write the contract KDoc (Option 3a) as one
`📝` PR after #277.** Decline 2b, 3b and 3c. Leave `Effect` as it is unless the maintainer wants
to close #138 differently (D2).

Rationale:

1. **The code is moving toward the markers, not away from them.** Master already routes signals by
   `T : Signal` and events by `A : Event`, and keys ViewModels by the state class. #271 makes
   `anchor()` dispatch by `S::class`. #277 routes *held* signals by subtype and adds public API
   typed on `Signal`. #279 exposes `S` to Swift through a generic class. In each case the bound
   turns a silent runtime misroute into a compile error (M1-M7).
2. **The saving is small and the break is not.** Removal saves one supertype per type (9
   declarations across 3 sample features). It costs a JVM and iOS binary break across 39 public
   declarations and 13 or more iOS linkage signatures in `anchor` alone. It forces edits to three
   open PRs and one API #277 is about to publish. It needs a doc and plan sweep over 6 docs and
   17 plan and spec files.
3. **#133's premise is partly stale.** The markers now have KDoc, and "no type safety" conflates
   "anyone can opt in" with "nobody is checked": the checking is the point (§1.5). #133 offered
   "make them provide actual value" as an alternative. Here the value is compile-time validation
   of the channel split, which #145 itself lists under "What's Good" ("Separation of
   State/Signal/Event - Conceptually sound (execution needs work)"). What was missing was the written contract, which
   Option 1 supplies.
4. **The pre-1.0 window stays open for `Effect` only.** It is the one marker whose bound protects
   nothing (§1.7). If it is ever relaxed, do it in the same breaking release as #271 (0.1.9 per
   plan 027) and before plan 029's dumps become the baseline. After 1.0 it costs a major version.

If the maintainer **overrides to remove** (2a or 2b):
- relax to `Any`, not unbounded (§2);
- decide before #277 merges;
- bundle the change with #271's break in 0.1.9 and keep the interfaces `@Deprecated` for one
  release (2a before 2b);
- add a KDoc rule to #271's `anchor()` and to `RememberAnchor`: "`S` must be a class dedicated to
  this anchor";
- sweep the plans directory, as the plan's maintenance note says.

### Decisions needed (maintainer)

- **D1**: keep (Option 1, recommended), 2a, 2b, or 2′?
- **D2**: `Effect`. Keep for symmetry (recommended), or relax to `R : Any` now, which retires
  `EmptyEffect` and settles #138? If relaxing, it must happen before #277/#279 merge and before
  029's baseline.
- **D3**: should `ViewState`'s KDoc state the "one dedicated state class per `RememberAnchor`, else
  `customKey`" rule? This is only meaningful once #271 lands, and it is recommended then.
- **D4**: where the contract KDoc lands. Either a standalone `📝` PR after #277 (recommended), or
  folded into plan 028 (#140 docs overhaul) together with plan 017's semantics table.
- **D5**: tracker. Reopen #133 or annotate it? Relabel the closure (plan 027 spec Q5 proposes
  "not planned"/"decided"). Correct #237's checkbox text. Drafts are in Appendix B.
- **D6**: confirm the sub-marker direction (`NavigationSignal`, #169/#242) is declined, and note it
  on #169, which currently reads COMPLETED with no code on master.

---

## Appendix A: Evidence

### A.1 Prototype (scratchpad, not committed)

These prototypes are standalone scratchpad files with no project build and no worktree. They
were compiled with
`java -cp <kotlin-compiler-embeddable-2.4.0 + deps from ~/.gradle/caches> org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -classpath kotlin-stdlib-2.4.0.jar …`.

Library variant A (master's shapes):

```kotlin
interface ViewState; interface Effect; interface Signal; interface Event
object EmptyEffect : Effect
interface StateAnchor<S : ViewState> { val state: S }
interface SignalProvider { fun provide(): Signal }
abstract class Anchor<R : Effect, S : ViewState, Err : Any> : StateAnchor<S> {
  abstract suspend fun post(block: () -> Signal)
  abstract suspend fun emit(block: () -> Event)
}
fun <R : Effect, S : ViewState, Err : Any> create(effectScope: () -> R, initialState: () -> S): Anchor<R, S, Err> = TODO()
inline fun <reified T : Signal> handleSignal(noinline block: (T) -> Unit) { println(T::class) }
inline fun <reified A : Event> connect(noinline block: (A) -> Unit) { println(A::class) }
inline fun <R : Effect, reified S : ViewState, Err : Any> anchor(noinline block: suspend Anchor<R, S, Err>.() -> Unit): () -> Unit = { println(S::class) }
```

Variant B is identical with every marker bound and marker-typed return replaced by `Any`. The
interfaces are still declared.

Consumer (compiled against both):

```kotlin
data class CounterState(val count: Int = 0) : ViewState
sealed interface CounterSignal : Signal { object Inc : CounterSignal }
sealed interface CounterEvent : Event { object Refresh : CounterEvent }
typealias CounterAnchor = Anchor<EmptyEffect, CounterState, Nothing>
data class PlainState(val count: Int = 0)          // forgot ": ViewState"
class Repository                                   // not ": Effect"
suspend fun CounterAnchor.misroute() {
  post { CounterEvent.Refresh }                    // M1
  emit { CounterSignal.Inc }                       // M2
}
fun ui() {
  handleSignal<CounterEvent> { }                   // M3
  connect<CounterSignal> { }                       // M4
  handleSignal<String> { }                         // M5
}
fun primitiveState() = create<EmptyEffect, Int, Nothing>({ EmptyEffect }, { 0 })                  // M6
fun plainState() = create<EmptyEffect, PlainState, Nothing>({ EmptyEffect }, { PlainState() })     // M7
fun repoEffect() = create<Repository, CounterState, Nothing>({ Repository() }, { CounterState() }) // M8
```

Output (variant A; variant B produced no diagnostics and exit 0):

```
consumer.kt:10:10: error: return type mismatch: expected 'Signal', actual 'CounterEvent.Refresh'.
consumer.kt:11:10: error: return type mismatch: expected 'Event', actual 'CounterSignal.Inc'.
consumer.kt:14:16: error: type argument is not within its bounds: type parameter 'T (of fun <T : Signal> handleSignal)' must be subtype of 'Signal', but actual: 'CounterEvent'.
consumer.kt:14:30: error: argument type mismatch: actual type is '(CounterEvent) -> Unit', but '(CounterEvent) -> Unit' was expected.
consumer.kt:15:11: error: type argument is not within its bounds: type parameter 'A (of fun <A : Event> connect)' must be subtype of 'Event', but actual: 'CounterSignal'.
consumer.kt:16:16: error: type argument is not within its bounds: type parameter 'T (of fun <T : Signal> handleSignal)' must be subtype of 'Signal', but actual: 'String'.
consumer.kt:18:44: error: type argument is not within its bounds: type parameter 'S (of fun <R : Effect, S : ViewState, Err : Any> create)' must be subtype of 'ViewState', but actual: 'Int'.
consumer.kt:19:40: error: type argument is not within its bounds: type parameter 'S (of fun <R : Effect, S : ViewState, Err : Any> create)' must be subtype of 'ViewState', but actual: 'PlainState'.
consumer.kt:20:27: error: type argument is not within its bounds: type parameter 'R (of fun <R : Effect, S : ViewState, Err : Any> create)' must be subtype of 'Effect', but actual: 'Repository'.
(+ 8 secondary "argument type mismatch" lines of the '() -> X' vs '() -> X' kind)
```

Variant C, `inline fun <reified S> keyOf() = S::class.qualifiedName.orEmpty()`: exit 0.

Variant D, the iOS wrapper with an unbounded `S`:

```
lib_d.kt:5:76: error: type argument is not within its bounds: type parameter 'T (of class NativeStateFlow<T : Any>)' must be subtype of 'Any', but actual: 'S (of fun <S> nativeViewStateUnbounded)'.
```

JVM descriptors (`javap -s`), A against B:

```
A  public abstract S getState();              descriptor: ()Lproto/ViewState;
A  public abstract proto.Signal provide();    descriptor: ()Lproto/Signal;
B  public abstract S getState();              descriptor: ()Ljava/lang/Object;
B  public abstract java.lang.Object provide(); descriptor: ()Ljava/lang/Object;
```

### A.2 The 39 public declarations with a marker in their signature (master)

- `anchor` commonMain (20):
  - `Anchor`, `AnchorSink`, `StateAnchor`, `MutableStateAnchor`, `EffectAnchor`,
    `CancellableAnchor`, `BaseAnchorScope`, `AnchorScope`
  - `RememberAnchorScope.create`, `SubscriptionsScope`, `SubscriptionsScope.connect`
  - `SignalProvider.provide`, `SignalAnchor.post`, `SubscriptionAnchor.emit`
  - `Anchor.recover`, `ContainerViewModel`, `anchorContainerViewModelFactory`
  - `catchDomainError`, `catchDefects`, `safeExecute` (public in `dev.kioba.anchor.internal`;
    see plan 007)
- `anchor` iosMain (3): `rememberAnchor`, `nativeViewState`, `nativeSignals`.
- `anchor-compose` (8): `anchor` ×4, `HandleSignal`, `AnchorStateScope`, `PreviewAnchor`,
  `RememberAnchor`.
- `anchor-test` (8): `runAnchorTest`, `runAnchorSequenceTest`, `AnchorTestScope`,
  `AnchorSequenceTestScope`, `AnchorStepScope`, `GivenScope`, `StepGivenScope`, `VerifyScope`
  (whose `assertSignal`/`assertEvent` take `() -> Signal`/`() -> Event`).

### A.3 Census script

The census was computed per ref with `git show`, so no checkout was needed. It covers
`git ls-tree -r --name-only <ref> -- anchor anchor-compose anchor-test` filtered to
`/src/*Main/*.kt`, skipping KDoc and comment lines. Each file is then counted with:

```perl
# bound sites
$n++ while /\b[A-Z][A-Za-z]{0,2}\s*:\s*(Effect|ViewState|Signal|Event)\b(?![A-Za-z])/g
# direct type uses (import lines skipped)
/(->\s*(Signal|Event)\b(?![A-Za-z])|<(Event|Signal|Effect|ViewState)[,>]|\(\):\s*Signal\b(?![A-Za-z])|\((Signal|Event)\)\s*->)/
# projections
/\bout (Effect|ViewState)\b/
```

Refs: `origin/master` `0bc430c`; `origin/fix/022-anchor-action-type-resolution` `2651e8f` (#271);
`origin/fix/024-init-signal-delivery` `cd2a799` (#277); `origin/fix/005-ios-anchor-lifecycle`
`a88f287` (#279); `origin/fix/003-subscription-restart` `50c38b3` (#280).

---

## Appendix B: Tracker drafts (for the maintainer to post; nothing has been posted)

### B.1 Comment for #133 (use it whether you reopen the issue or only relabel the closure)

> **Decision record** (this issue was closed on 2026-06-02 without one). Design note:
> `plans/design/marker-interfaces.md` on `plans/issue-triage-2026-09-29` (#269).
>
> **Decision: keep `ViewState`, `Effect`, `Signal` and `Event` as generic bounds. Don't remove
> them, and don't add behavior to them.** Their job is compile-time validation of the
> State/Signal/Event split.
>
> - With the bounds, these misuses fail to compile: posting an `Event` as a signal, emitting a
>   `Signal` as an event, `HandleSignal<SomeEvent>`, `connect<SomeSignal>`,
>   `HandleSignal<String>`, and a primitive or forgotten state class. With `: Any` bounds, as in
>   Orbit and MVIKotlin, all of them compile and fail silently. That is shown with a Kotlin 2.4.0
>   prototype in the note.
> - Upcoming work routes on these types. `anchor()` resolves by `ViewState` class (#271), and held
>   signals are handed to the `HandleSignal<T>` of the matching `Signal` subtype (#277).
> - Removal would break binaries on JVM and iOS: 39 public declarations, and 13 Kotlin/Native
>   linkage signatures in `:anchor` alone. It would save one supertype per declared type.
>
> Two points in the original report are out of date. The markers now live in `Anchor.kt` and have
> KDoc (#153). The value is in what the bound *rejects*, not in members.
>
> Follow-up: the KDoc of each marker will state what the bound guarantees and the delivery
> contract of its channel (tracked with #140). `Effect` is the weakest of the four, because
> nothing routes on it; whether to relax it to `Any` before 1.0 is recorded as an open question
> (see also #138).

### B.2 Correction for the #237 "Pre-1.0" checkbox

Replace
`- [x] **#133** — Decide on marker interfaces (…). Removal breaks every consumer; needs a design note before action.`
with:

> `- [x] **#133** — Marker interfaces: **kept** as bounds (decision record: #133 comment, design note
> plans/design/marker-interfaces.md). Follow-up: contract KDoc on each marker, with #140.`

If the maintainer picks D1 = 2a/2b or D2 = relax, instead use:

> `- [ ] **#133** — Marker interfaces: relax bounds to `Any` (…) before #277 merges; breaking, ship with
> #271 in 0.1.9.`

### B.3 Optional note for #169 (D6)

> Closing note: #242 (`NavigationSignal : Signal`) was closed unmerged, so nothing shipped. The
> sub-marker direction is declined for now (see the #133 decision record); revisit it if a second
> consumer appears.
