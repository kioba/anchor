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
public inline fun <reified R, reified S> runAnchorTest(
  noinline builder: RememberAnchorScope.() -> Anchor<R, S, *>,
  crossinline block: suspend AnchorTestScope<R, S>.() -> Unit,
): TestResult where R : Effect, S : ViewState =
  runTest {
    AnchorTestScope(builder)
      .apply { block() }
      .assert()
  }
