package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.ViewState
import dev.kioba.anchor.test.scopes.AnchorTestScope
import dev.kioba.anchor.test.scopes.assert
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest

@AnchorTestDsl
public inline fun <reified E, reified S> runAnchorTest(
  noinline builder: RememberAnchorScope.() -> Anchor<E, S>,
  crossinline block: suspend AnchorTestScope<E, S>.() -> Unit,
): TestResult where E : Effect, S : ViewState =
  runTest {
    AnchorTestScope(builder)
      .apply { block() }
      .assert()
  }
