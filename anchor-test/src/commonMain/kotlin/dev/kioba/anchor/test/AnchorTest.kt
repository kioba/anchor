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

public inline fun <reified R, reified S, Err : Any> runAnchorTest(
  noinline builder: RememberAnchorScope.() -> Anchor<R, S, Err>,
  crossinline block: suspend AnchorTestScope<R, S, Err>.() -> Unit,
): TestResult where R : Effect, S : ViewState =
  runTest {
    AnchorTestScope(builder)
      .apply { block() }
      .assert()
  }

public inline fun <reified R, reified S, Err : Any> runAnchorSequenceTest(
  noinline builder: RememberAnchorScope.() -> Anchor<R, S, Err>,
  crossinline block: suspend AnchorSequenceTestScope<R, S, Err>.() -> Unit,
): TestResult where R : Effect, S : ViewState =
  runTest {
    AnchorSequenceTestScope(builder)
      .apply { block() }
      .assertSequence()
  }
