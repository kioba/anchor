package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.dsl.and
import dev.kioba.anchor.dsl.chain
import dev.kioba.anchor.dsl.listenCreated
import dev.kioba.anchor.dsl.until
import dev.kioba.anchor.features.main.presentation.model.Cancel
import dev.kioba.anchor.features.main.presentation.model.Refresh
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

context(MainEffects)
  public fun helloListening(): Flow<Int> =
  flow {
    repeat(30) { iteration ->
      emit(iteration)
      delay(500)
    }
  }

context (MainSubScope)
  internal fun subscriptions() {

  listenCreated()
    .and<Refresh>()
    .until<Cancel>()
    .chain { helloListening() }
    .anchor(::iterationCounter)
}
