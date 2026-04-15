package dev.kioba.anchor.features.main.data

import dev.kioba.anchor.features.main.model.MainEvent
import kotlinx.coroutines.delay

internal fun MainAnchor.onError(
  n: Nothing,
) {
  TODO()
}

internal fun MainAnchor.defect(
  throwable: Throwable,
) {
  reduce {
    copy(
      errorDialog =
        """A propagated error occurred:
    |${throwable.message.orEmpty()}
        """.trimMargin(),
    )
  }
}

public suspend fun MainAnchor.sayHi() {
  cancellable("Initial") {
    reduce { copy(details = "Hello Android!") }
  }
}

public suspend fun MainAnchor.clear() {
  effect { delayWithEffect() }
  emit { MainEvent.Cancel }
  reduce {
    copy(
      details = "cleared",
      iterationCounter = null,
    )
  }
}

public suspend fun MainAnchor.refresh(): Unit =
  emit { MainEvent.Refresh }

public fun MainAnchor.selectHome() {
  reduce { updateHomeSelected() }
}

public fun MainAnchor.selectCounter(): Unit =
  reduce { updateCounterSelected() }

public fun MainAnchor.selectQuack(): Unit =
  reduce { updateProfileSelected() }

public fun MainAnchor.iterationCounter(
  value: Int,
) {
  reduce { copy(details = "refreshed") }
  reduce { copy(iterationCounter = value.toString()) }
}

public fun MainAnchor.hundreds(): Int =
  withState { hundreds }

public suspend fun MainAnchor.triggerLocalError() {
  try {
    effect { failingEffect() }
  } catch (
    @Suppress("TooGenericExceptionCaught") e: Exception,
  ) {
    reduce { copy(errorDialog = "A locally caught error occurred.") }
  }
}

public suspend fun MainAnchor.triggerPropagatedError() {
  effect { failingEffect() }
}

public fun MainAnchor.dismissErrorDialog() {
  reduce { copy(errorDialog = null) }
}

@Suppress("UnusedReceiverParameter")
public suspend fun MainEffect.failingEffect(): Nothing =
  throw RuntimeException("Example error from effect")

@Suppress("UnusedReceiverParameter")
public suspend fun MainEffect.delayWithEffect(): Int =
  delay(500).let { 1 }
