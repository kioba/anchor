package dev.kioba.anchor.features.main.data

import dev.kioba.anchor.features.main.model.MainEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

internal suspend fun MainSubScope.subscriptions() {
  listen(::refresh)
}

internal fun MainEffect.helloListening(): Flow<Int> =
  generateSequence(seed = 0) { it.inc() }
    .asFlow()
    .onEach { delayWithEffect() }

@OptIn(ExperimentalCoroutinesApi::class)
internal fun MainSubScope.refresh(
  flow: Flow<MainEvent>,
): Flow<Int> =
  flow
    .filter { it is MainEvent.Refresh || it is MainEvent.Cancel }
    .flatMapLatest { mainEvent ->
      when (mainEvent) {
        MainEvent.Refresh -> effect.helloListening()
        MainEvent.Cancel -> emptyFlow()
      }
    }.anchor(MainAnchor::iterationCounter)
