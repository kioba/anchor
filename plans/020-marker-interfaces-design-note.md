# Plan 020: Write the missing #133 design note — keep, enrich, or remove the marker interfaces

> **Executor instructions**: This is a DESIGN plan. The deliverable is a
> design note at `plans/design/marker-interfaces.md` — NOT a code change and
> NOT tracker actions (reopening/commenting #133 is the maintainer's call;
> draft the comment text in the note's appendix). Do not modify any file
> outside `plans/`. When done, update the status row in `plans/README.md`.
>
> **Drift check (run first)**: `git fetch origin --quiet; git show origin/master:anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt | head -60`
> Confirm the four marker interfaces still exist unchanged on origin/master;
> if they changed, this plan becomes a review of that change.

## Status

- **Priority**: P2 (gates every new public-API addition before 1.0)
- **Effort**: S
- **Risk**: LOW (note only; the eventual decision ranges from zero-change to
  break-every-consumer)
- **Depends on**: none
- **Category**: direction
- **Planned at**: commit `492f7bc` (origin/master `ec1017c`), 2026-06-11

## Why this matters

Issue **#133** ("Remove confusing marker interfaces that add no value",
flagged by the staff-engineer review #145 as a pre-1.0 blocker) was closed
COMPLETED on 2026-06-02 **with no comments and no code change** — all four
markers are intact on master. The roadmap issue #237 has its checkbox ticked
against the stated requirement "needs a design note before action"; the
design note does not exist. Meanwhile every addition to the public surface
(the pending AnchorEffect/AnchorConsumer/NavigationSignal PRs all bound to
these types — `NavigationSignal : Signal`) deepens the commitment. Whatever
the decision is, it must exist in writing before 1.0; the cheapest possible
version of that is this note.

## Current state

- `anchor/src/commonMain/kotlin/dev/kioba/anchor/Anchor.kt:15-53` — the four
  markers plus their companions:

  ```kotlin
  public interface ViewState
  public interface Effect
  public object EmptyEffect : Effect
  public interface Signal
  public object UnitSignal : Signal
  public interface Event
  public object Created : Event
  ```

- They are load-bearing in three distinct ways (the note must address each):
  1. **Generic bounds everywhere**: `Anchor<R, S, Err> where R : Effect, S : ViewState`
     (`Anchor.kt`), `AnchorScope`, the test DSL, the compose layer — removal
     ripples through every signature.
  2. **Reified runtime filtering**: `HandleSignal<reified T : Signal>`
     (`anchor-compose/.../LocalSignal.kt`) and `listen<reified A : Event>`
     (`SubscriptionDsl.kt:68-74`) — the bound is what keeps these type-safe.
  3. **Companion defaults**: `EmptyEffect`, `UnitSignal`, `Created` are
     instances, so the "interfaces" double as an extension point.
- Consumer-side cost (the #133 complaint): every app type declares
  `: ViewState` / `: Effect` / `: Signal` / `: Event` for zero behavior.
- Tracker context: #133 closed COMPLETED 2026-06-02, no comments; #145 open,
  "critical"; #237 "Pre-1.0" section: "[x] #133 — ... needs a design note
  before action"; commit `c835d3c` addressed #133-adjacent scope cleanup but
  not the markers.

## Commands you will need

| Purpose | Command | Expected on success |
|---------|---------|---------------------|
| Bound census | `grep -rn ": ViewState\|: Effect\|: Signal\|: Event\|where R : Effect\|S : ViewState" --include="*.kt" anchor anchor-compose anchor-test \| grep -v build/ \| wc -l` | the blast-radius number for §2 |
| Issue context | `gh issue view 133 --repo kioba/anchor; gh issue view 145 --repo kioba/anchor` | option framing from the original review |

## Scope

**In scope** (writable): `plans/design/marker-interfaces.md`, `plans/README.md`.

**Out of scope**: any Kotlin change; reopening/closing issues; ROADMAP.md.

## Steps

### Step 1: Establish what the markers actually buy

For each of the three load-bearing roles above, document with a concrete
in-repo example what breaks or degrades without the bound — e.g. what
`HandleSignal<T>` looks like with `T : Any` (any type can be "handled";
typos compile), what `create()` inference does without `S : ViewState`.
Honestly include what they DON'T buy (no behavior, no exhaustiveness, any
class can opt in).

### Step 2: Lay out the three options with migration math

1. **Keep as-is, document the contract** — markers stay; their KDoc gains the
   rationale (type-driven DSL routing). Migration cost: zero. Cost: permanent
   4-interface ceremony per feature.
2. **Remove the bounds** (`Anchor<R, S, Err>` unconstrained or `R : Any`) —
   consumer ceremony gone. Quantify: the bound census number, which public
   signatures change, what `HandleSignal`/`listen` lose, what happens to
   `EmptyEffect`/`UnitSignal`/`Created` (they need somewhere to live), and
   that this breaks **every consumer** (pre-1.0 license, but it's the big one).
3. **Enrich instead of remove** — markers gain meaning (e.g.
   `Signal` stays a marker but gains documented semantics; or sealed-friendly
   helper supertypes like `NavigationSignal : Signal` — note the pending PR
   #242/#169 work as evidence this direction is already being walked).
   Middle cost, middle payoff.

### Step 3: Recommend + draft the tracker text

§3: recommendation with rationale; §appendix: the comment text the maintainer
can paste into #133 (reopen-or-annotate) and the #237 checkbox correction.
**STOP** — the maintainer executes the tracker actions and any code follow-up.

## Test plan

None — design note. The bound census command's output is the only "measurement".

## Done criteria

- [ ] `plans/design/marker-interfaces.md` with §1 roles-and-value analysis,
      §2 three options with the blast-radius census, §3 recommendation,
      appendix tracker-comment draft
- [ ] No file outside `plans/` modified (`git status`)
- [ ] `plans/README.md` row updated

## STOP conditions

- End of Step 3 is mandatory.
- If `Anchor.kt`'s markers changed on origin/master, or #133 gained a design
  note since planning, switch to reviewing that instead.

## Maintenance notes

- Plans 001/002/009 fixtures and the pending DX-composable PRs all implement
  these markers; if option 2 (removal) is ever chosen, every plan in this
  directory that says `: ViewState` needs a sweep — note it in the index at
  decision time.
- This note plus plan 017's semantics table are the two inputs #140's docs
  overhaul ("State vs Signal vs Event guarantees") needs anyway — write them
  to be quotable.
