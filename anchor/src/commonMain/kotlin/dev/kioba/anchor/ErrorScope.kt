package dev.kioba.anchor

/**
 * Semantic alias for [BaseAnchorScope] used as the receiver in
 * `onDomainError` and `defect` handler lambdas.
 *
 * Using [ErrorScope] in handler signatures makes intent clear:
 * the handler can modify state and execute effects, but cannot
 * call [Raise.raise] or [DefectAnchor.orDie].
 *
 * ```kotlin
 * create(
 *   onDomainError = { error -> reduce { copy(errorMessage = error.message) } },
 *   defect = { throwable -> post { ErrorSignal(throwable) } },
 * )
 * ```
 *
 * @param R The [Effect] type.
 * @param S The [ViewState] type.
 */
public typealias ErrorScope<R, S> = BaseAnchorScope<R, S>
