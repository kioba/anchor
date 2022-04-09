package dev.kioba.anchor.example

import dev.kioba.anchor.AnchorDslSyntax
import dev.kioba.anchor.AnchorEnvironment
import dev.kioba.anchor.dsl.action
import dev.kioba.anchor.dsl.reduce

internal class MainSyntax(
  override val environment: AnchorEnvironment<MainViewState> = AnchorEnvironment(
    initialState = { MainViewState(text = "Hey") },
    initialAction = sayHi()
  ),
) : AnchorDslSyntax<MainViewState>

internal fun sayHi() =
  action<MainSyntax> {
    reduce { copy(text = "Hello Android!") }
  }

internal fun clicked() =
  action<MainSyntax> {
    reduce { copy(text = "clicked") }
  }
