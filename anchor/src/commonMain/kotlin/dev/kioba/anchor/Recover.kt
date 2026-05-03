package dev.kioba.anchor

import dev.kioba.anchor.internal.RaiseScope
import dev.kioba.anchor.internal.RaiseToken

/**
 * Represents the result of a recoverable operation that may raise a domain error.
 *
 * @param Err The domain error type.
 * @param T The success value type.
 */
public sealed interface Recover<out Err, out T> {
  /**
   * Successful result containing a value.
   */
  public data class Ok<T>(public val value: T) : Recover<Nothing, T>

  /**
   * Failed result containing a domain error.
   */
  public data class Error<Err>(public val error: Err) : Recover<Err, Nothing>
}

// -- Anchor-level recover --

/**
 * Executes [block] on this [Anchor] and catches any [RaisedException],
 * wrapping the outcome in a [Recover].
 *
 * Use this inside an Anchor action to attempt an operation that may
 * call [Raise.raise] and handle the error locally instead of propagating
 * to the `onDomainError` handler.
 *
 * ```kotlin
 * suspend fun MyAnchor.loadUser(id: Int) {
 *   recover { validate(id) }
 *     .getOrElse { reduce { copy(error = it) }; return }
 * }
 * ```
 */
public suspend inline fun <R, S, Err, T> Anchor<R, S, Err>.recover(
  crossinline block: suspend Anchor<R, S, Err>.() -> T,
): Recover<Err, T> where R : Effect, S : ViewState, Err : Any =
  try {
    Recover.Ok(block())
  } catch (e: RaisedException) {
    @Suppress("UNCHECKED_CAST")
    Recover.Error(e.error as Err)
  }

// -- Standalone recover --

/**
 * Executes [block] in a scoped [Raise] context and catches any
 * [RaisedException] that was raised within that exact scope.
 *
 * The scope is identified by a [RaiseToken]; errors raised in nested
 * `recover` blocks or in an outer [Anchor] are not caught here.
 *
 * ```kotlin
 * val result: Recover<MyError, String> = recover { ensure(valid) { MyError }; "ok" }
 * ```
 */
public inline fun <Err : Any, T> recover(
  block: Raise<Err>.() -> T,
): Recover<Err, T> {
  val token = RaiseToken()
  return try {
    Recover.Ok(RaiseScope<Err>(token).block())
  } catch (e: RaisedException) {
    if (e.token === token) {
      @Suppress("UNCHECKED_CAST")
      Recover.Error(e.error as Err)
    } else {
      throw e
    }
  }
}

// -- Extensions --

/**
 * Returns the success value or `null`.
 */
public fun <Err, T> Recover<Err, T>.getOrNull(): T? =
  when (this) {
    is Recover.Ok -> value
    is Recover.Error -> null
  }

/**
 * Returns the error or `null`.
 */
public fun <Err, T> Recover<Err, T>.getErrorOrNull(): Err? =
  when (this) {
    is Recover.Ok -> null
    is Recover.Error -> error
  }

/**
 * Escalates the error to a defect via [DefectAnchor.orDie],
 * or returns the success value.
 */
public fun <Err : Any, T> DefectAnchor<Err>.orDie(
  result: Recover<Err, T>,
): T =
  when (result) {
    is Recover.Ok -> result.value
    is Recover.Error -> orDie(result.error)
  }

/**
 * Transforms the [Recover] into a single value by applying [onError]
 * for [Recover.Error] or [onOk] for [Recover.Ok].
 */
public inline fun <Err, T, V> Recover<Err, T>.fold(
  onError: (Err) -> V,
  onOk: (T) -> V,
): V =
  when (this) {
    is Recover.Ok -> onOk(value)
    is Recover.Error -> onError(error)
  }

/**
 * Returns the success value, or the result of [onError].
 */
public inline fun <Err, T> Recover<Err, T>.getOrElse(
  onError: (Err) -> T,
): T =
  when (this) {
    is Recover.Ok -> value
    is Recover.Error -> onError(error)
  }
