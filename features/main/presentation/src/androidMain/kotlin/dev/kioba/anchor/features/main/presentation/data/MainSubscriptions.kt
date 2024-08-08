package dev.kioba.anchor.features.main.presentation.data

import dev.kioba.anchor.features.main.presentation.model.MainEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.scan
import kotlin.time.Duration.Companion.milliseconds

@Suppress("UnusedReceiverParameter")
internal fun MainEffects.helloListening(
  hundreds: Int,
): Flow<Int> =
  flow {
    repeat(30) { iteration ->
      emit(hundreds * 100 + iteration)
      delay(200.milliseconds)
    }
  }

@OptIn(ExperimentalCoroutinesApi::class)
internal suspend fun MainSubScope.subscriptions() {
  listen<MainEvent.Refresh> { flow ->
    flow
      .scan(0) { acc, _ -> acc + 1 }
      .drop(1)
      .flatMapLatest { helloListening(it) }
      .anchor(::iterationCounter)
  }
}
