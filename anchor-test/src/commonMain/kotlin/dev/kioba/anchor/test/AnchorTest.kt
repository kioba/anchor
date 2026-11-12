package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.scopes.AnchorSequenceTestScope
import dev.kioba.anchor.test.scopes.AnchorTestScope
import dev.kioba.anchor.test.scopes.assert
import dev.kioba.anchor.test.scopes.assertSequence
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

/**
 * Tests one action of the anchor built by [builder]: `given` sets up the initial state and effect
 * scope, `on` supplies the action, and `verify` lists the actions it must record, in order.
 *
 * The action runs once, to completion, on a recording test runtime inside [runTest], so `delay`
 * uses virtual time. This verifies action logic deterministically (the state it reduces, the
 * signals it posts, the events it emits and the errors it raises) but not how the production
 * runtime schedules and delivers them:
 *
 * - The factory's `init` and `subscriptions` blocks never run.
 * - `cancellable(key)` runs its block inline and never cancels a still-running block with the same
 *   key.
 * - `effect(context)` ignores `context` and runs in the caller's context.
 * - `post` and `emit` only record. `assertSignal` and `assertEvent` prove that the action sent a
 *   signal or event, not that a collector or `connect()` handler received it.
 * - `effect` calls are not recorded, so `assertEffect` cannot confirm that one ran (see
 *   [dev.kioba.anchor.test.scopes.VerifyScope.assertEffect]).
 *
 * Cover cancellation, debouncing, dispatcher use, `init`, subscriptions and signal delivery with a
 * test that drives the production runtime through a public entry point: the `ContainerViewModel`
 * that `anchorContainerViewModelFactory` creates (call `execute`, observe `viewState` and
 * `signals`; its `ViewModel` supertype needs `androidx.lifecycle:lifecycle-viewmodel` on the test
 * compile classpath), or `RememberAnchor` from `anchor-compose`.
 */
public inline fun <reified R, reified S, Err : Any> runAnchorTest(
  noinline builder: RememberAnchorScope.() -> Anchor<R, S, Err>,
  crossinline block: suspend AnchorTestScope<R, S, Err>.() -> Unit,
): TestResult where R : Effect, S : ViewState =
  runTest {
    AnchorTestScope(builder)
      .apply { block() }
      .assert()
  }

/**
 * Tests a sequence of actions on the anchor built by [builder]. Each `step` runs its action on a
 * fresh recording test runtime that starts from the previous step's final state and shares one
 * effect scope instance with the other steps, then verifies it like [runAnchorTest]. Steps run one
 * after another, never concurrently.
 *
 * The limits listed on [runAnchorTest] apply here too: `init` and `subscriptions` never run,
 * `cancellable` never cancels a running block, `effect` ignores its context, and `post` and `emit`
 * only record.
 */
public inline fun <reified R, reified S, Err : Any> runAnchorSequenceTest(
  noinline builder: RememberAnchorScope.() -> Anchor<R, S, Err>,
  crossinline block: suspend AnchorSequenceTestScope<R, S, Err>.() -> Unit,
): TestResult where R : Effect, S : ViewState =
  runTest {
    AnchorSequenceTestScope(builder)
      .apply { block() }
      .assertSequence()
  }
