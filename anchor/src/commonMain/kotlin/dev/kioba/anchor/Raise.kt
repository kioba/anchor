package dev.kioba.anchor

/**
 * Provides the ability to raise domain errors via short-circuit.
 *
 * This is a general-purpose interface that can be used as a receiver on
 * pure domain functions, validators, or mappers without depending on the
 * full [Anchor] type.
 *
 * When used with [Anchor], `raise` propagates the error to the nearest
 * `recover` block or the `onDomainError` handler configured via `create()`.
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
  @AnchorDsl
  public fun raise(error: Err): Nothing
}
