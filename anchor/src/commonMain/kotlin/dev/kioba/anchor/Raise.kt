package dev.kioba.anchor

/**
 * Provides the ability to raise domain errors via short-circuit.
 *
 * This is a general-purpose interface that can be used as a receiver on
 * pure domain functions, validators, or mappers without depending on the
 * full [Anchor] type.
 *
 * When used with [Anchor], `raise` propagates the error to the
 * `onDomainError` handler configured via `create()`.
 *
 * For [PureAnchor] (`Anchor<R, S, Nothing>`), `raise(Nothing)` is statically
 * uncallable — the type system prevents misuse with zero runtime cost.
 *
 * @param Err The domain error type.
 */
@AnchorDsl
public interface Raise<Err> where Err : Any {
  /**
   * Short-circuits execution with a domain error.
   *
   * @param error The domain error to raise.
   */
  public fun raise(error: Err): Nothing

  /**
   * Ensures that [condition] is true, otherwise raises a domain error.
   *
   * The [error] lambda is only evaluated when the condition is false.
   *
   * Example:
   * ```kotlin
   * ensure(user.isActive) { UserError.Inactive }
   * ```
   *
   * @param condition The condition to check.
   * @param error A lazy provider for the domain error to raise when the condition is false.
   */
  public fun ensure(condition: Boolean, error: () -> Err) {
    if (!condition) raise(error())
  }

  /**
   * Unwraps a [Recover.Ok] value or re-raises the [Recover.Error].
   *
   * This is a member extension so it is only callable inside a [Raise]
   * scope (Anchor actions, standalone `recover` blocks).
   *
   * ```kotlin
   * val user: User = recover { fetchUser(id) }.getOrRaise()
   * ```
   */
  @AnchorDsl
  public fun <T> Recover<Err, T>.getOrRaise(): T =
    when (this) {
      is Recover.Ok -> value
      is Recover.Error -> raise(error)
    }
}
