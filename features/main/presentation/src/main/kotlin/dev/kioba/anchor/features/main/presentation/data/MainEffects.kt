package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.dsl.cancellable
import dev.kioba.anchor.dsl.effect
import dev.kioba.anchor.dsl.reduce
import dev.kioba.anchor.dsl.subscribe
import dev.kioba.anchor.features.main.presentation.model.Cancel
import dev.kioba.anchor.features.main.presentation.model.Refresh


context (MainScope)
  public suspend fun sayHi() {
  cancellable("Initial") {
    reduce { copy(details = "Hello Android!") }
  }
}

context (MainScope)
  public suspend fun clear() {
  reduce { copy(details = "cleared") }
  val value = effect { hello() }
  subscribe { Cancel }
  reduce { copy(iterationCounter = null) }
}

context (MainScope)
  public suspend fun refresh() {
  reduce { copy(details = "refreshed") }
  subscribe { Refresh }
  reduce { copy(iterationCounter = null) }
}

context (MainScope)
  public fun selectHome() {
  reduce { updateHomeSelected() }
}

context (MainScope)
  public fun selectProfile() {
  reduce { updateProfileSelected() }
}

context(MainScope)
  public fun iterationCounter(value: Int) {
  reduce { copy(iterationCounter = value.toString()) }
}