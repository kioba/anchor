package dev.kioba.anchor.test

import dev.kioba.anchor.Anchor
import dev.kioba.anchor.DomainDefectException
import dev.kioba.anchor.Effect
import dev.kioba.anchor.Event
import dev.kioba.anchor.RememberAnchorScope
import dev.kioba.anchor.Signal
import dev.kioba.anchor.ViewState
import kotlin.test.Test

private class HandlerEffect : Effect

private data class HandlerViewState(
  val value: Int = 0,
  val error: String = "",
) : ViewState

private sealed interface HandlerErr {
  data object NotFound : HandlerErr
  data class InvalidInput(val reason: String) : HandlerErr
}

private sealed interface HandlerSignal : Signal {
  data object ErrorOccurred : HandlerSignal
}

private sealed interface HandlerEvent : Event {
  data object ErrorLogged : HandlerEvent
}

private typealias HandlerAnchor = Anchor<HandlerEffect, HandlerViewState, HandlerErr>

private fun RememberAnchorScope.handlerAnchor(): HandlerAnchor =
  create(
    initialState = ::HandlerViewState,
    effectScope = { HandlerEffect() },
  )

class ErrorHandlerBehaviorTest {

  /**
   * Verifies that a domain error handler can call `reduce` to update state.
   * When `raise` propagates to the handler, the handler's reduce is captured
   * in verifyActions alongside the raise itself.
   */
  @Test
  fun domainErrorHandlerReducesState() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("handler that reduces") {
        onDomainError { reduce { copy(error = "handled") } }
      }

      on("raising") { raise(HandlerErr.NotFound) }

      verify("raise recorded, handler reduce captured") {
        assertRaise { HandlerErr.NotFound }
        assertState { copy(error = "handled") }
        assertDomainError { HandlerErr.NotFound }
      }
    }

  /**
   * Verifies that a domain error handler can post a signal. The signal
   * from the handler is captured in verifyActions and assertable with
   * assertSignal.
   */
  @Test
  fun domainErrorHandlerPostsSignal() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("handler that posts a signal") {
        onDomainError { post { HandlerSignal.ErrorOccurred } }
      }

      on("raising") { raise(HandlerErr.NotFound) }

      verify("raise recorded, handler signal captured") {
        assertRaise { HandlerErr.NotFound }
        assertSignal { HandlerSignal.ErrorOccurred }
        assertDomainError { HandlerErr.NotFound }
      }
    }

  /**
   * Verifies that a domain error handler can emit an event. The event
   * from the handler is captured in verifyActions and assertable with
   * assertEvent.
   */
  @Test
  fun domainErrorHandlerEmitsEvent() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("handler that emits an event") {
        onDomainError { emit { HandlerEvent.ErrorLogged } }
      }

      on("raising") { raise(HandlerErr.NotFound) }

      verify("raise recorded, handler event captured") {
        assertRaise { HandlerErr.NotFound }
        assertEvent { HandlerEvent.ErrorLogged }
        assertDomainError { HandlerErr.NotFound }
      }
    }

  /**
   * Verifies that a defect handler can call `reduce` to update state.
   * When `orDie` escalates, the defect handler's reduce is captured.
   */
  @Test
  fun defectHandlerReducesState() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("defect handler that reduces") {
        defect { t -> reduce { copy(error = t.message.orEmpty()) } }
      }

      on("calling orDie") { orDie(HandlerErr.NotFound) }

      verify("orDie recorded, handler reduce captured") {
        assertOrDie { HandlerErr.NotFound }
        assertState { copy(error = "Domain defect: ${HandlerErr.NotFound}") }
        assertDefect { DomainDefectException(HandlerErr.NotFound) }
      }
    }

  /**
   * Verifies that a defect handler can post a signal after orDie
   * escalation. The signal is captured and assertable.
   */
  @Test
  fun defectHandlerPostsSignal() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("defect handler that posts signal") {
        defect { post { HandlerSignal.ErrorOccurred } }
      }

      on("calling orDie") { orDie(HandlerErr.NotFound) }

      verify("orDie recorded, handler signal captured") {
        assertOrDie { HandlerErr.NotFound }
        assertSignal { HandlerSignal.ErrorOccurred }
        assertDefect { DomainDefectException(HandlerErr.NotFound) }
      }
    }

  /**
   * Verifies that actions performed before a raise are preserved in the
   * action list. The pre-raise reduce is recorded first, then the raise,
   * then the handler's reduce. All three appear in order.
   */
  @Test
  fun actionReduceThenRaiseWithHandler() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("handler that reduces on error") {
        onDomainError { reduce { copy(error = "caught") } }
      }

      on("reduce then raise") {
        reduce { copy(value = 1) }
        raise(HandlerErr.NotFound)
      }

      verify("pre-raise reduce, raise, and handler reduce all recorded") {
        assertState { copy(value = 1) }
        assertRaise { HandlerErr.NotFound }
        assertState { copy(error = "caught") }
        assertDomainError { HandlerErr.NotFound }
      }
    }

  /**
   * Verifies that the domain error handler receives the correct error
   * variant when using a sealed interface with data. The InvalidInput
   * variant carries a reason string that the handler can read.
   */
  @Test
  fun handlerReceivesCorrectErrorValue() =
    runAnchorTest(RememberAnchorScope::handlerAnchor) {
      given("handler that reads error variant") {
        onDomainError { err ->
          when (err) {
            is HandlerErr.InvalidInput -> reduce { copy(error = err.reason) }
            is HandlerErr.NotFound -> reduce { copy(error = "not found") }
          }
        }
      }

      on("raising InvalidInput") { raise(HandlerErr.InvalidInput("bad data")) }

      verify("handler reduced with the reason from InvalidInput") {
        assertRaise { HandlerErr.InvalidInput("bad data") }
        assertState { copy(error = "bad data") }
        assertDomainError { HandlerErr.InvalidInput("bad data") }
      }
    }
}
