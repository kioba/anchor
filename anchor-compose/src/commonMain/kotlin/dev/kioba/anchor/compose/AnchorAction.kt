package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

/**
 * Creates a type-safe action callback that executes on the Anchor the action is defined for.
 *
 * Use this within a [RememberAnchor] scope to convert Anchor actions into callbacks that
 * can be passed to UI event handlers like `onClick`, `onValueChange`, etc.
 *
 * The Anchor type is automatically inferred from the action function's receiver type.
 * Resolves the nearest enclosing [RememberAnchor] whose ViewState is `S`; actions of an outer
 * anchor called inside a nested one run on the outer anchor. Throws [IllegalStateException] if
 * no enclosing [RememberAnchor] provides `S`. Inside a [PreviewAnchor] for `S` the callback is a no-op.
 *
 * The returned callback is remembered against the resolved Anchor and [block], so it is
 * referentially stable across recompositions.
 *
 * @param R The [Effect] type, inferred from the receiver of [block]
 * @param S The [ViewState] type, inferred from the receiver of [block]. Selects the Anchor to run on.
 * @param Err The domain error type, inferred from the receiver of [block]
 * @param block The action to execute. This is typically a function reference to an
 *        action defined as an extension on your Anchor type.
 * @return A callback function with no parameters that can be passed to UI event handlers
 *
 * @sample
 * ```kotlin
 * @Composable
 * fun CounterScreen() {
 *   RememberAnchor(scope = { counterAnchor() }) { state ->
 *     Button(
 *       onClick = anchor(CounterAnchor::increment)
 *     ) {
 *       Text("Increment")
 *     }
 *   }
 * }
 * ```
 *
 * @see RememberAnchor For setting up the Anchor scope
 */
@Composable
public inline fun <R : Effect, reified S : ViewState, Err : Any> anchor(
  noinline block: suspend Anchor<R, S, Err>.() -> Unit,
): () -> Unit {
  val scope =
    LocalAnchors.current[S::class]
      ?: error("anchor(): no enclosing RememberAnchor provides ${S::class.simpleName}")
  return remember(scope, block) {
    {
      scope.execute {
        // Safe cast: the scope was registered under S::class by RememberAnchor.
        @Suppress("UNCHECKED_CAST")
        (this as Anchor<R, S, Err>).block()
      }
    }
  }
}

/**
 * Creates a type-safe action callback that accepts one parameter.
 *
 * Use this when your action needs to receive a value from a UI event, such as text input,
 * slider values, or item selections.
 *
 * Resolves the nearest enclosing [RememberAnchor] whose ViewState is `S`; actions of an outer
 * anchor called inside a nested one run on the outer anchor. Throws [IllegalStateException] if
 * no enclosing [RememberAnchor] provides `S`. Inside a [PreviewAnchor] for `S` the callback is a no-op.
 * The returned callback is referentially stable across recompositions.
 *
 * @param R The [Effect] type, inferred from the receiver of [block]
 * @param S The [ViewState] type, inferred from the receiver of [block]. Selects the Anchor to run on.
 * @param Err The domain error type, inferred from the receiver of [block]
 * @param I The type of the parameter the callback will accept
 * @param block The action to execute. Receives the Anchor as receiver and one parameter.
 * @return A callback function that accepts one parameter and can be passed to UI event handlers
 *
 * @sample
 * ```kotlin
 * @Composable
 * fun SearchScreen() {
 *   RememberAnchor(scope = { searchAnchor() }) { state ->
 *     TextField(
 *       value = state.query,
 *       onValueChange = anchor(SearchAnchor::updateQuery)
 *     )
 *   }
 * }
 *
 * // Action definition:
 * suspend fun SearchAnchor.updateQuery(query: String) {
 *   reduce { copy(query = query) }
 * }
 * ```
 *
 * @see RememberAnchor For setting up the Anchor scope
 */
@Composable
public inline fun <R : Effect, reified S : ViewState, Err : Any, I> anchor(
  noinline block: suspend Anchor<R, S, Err>.(I) -> Unit,
): (I) -> Unit {
  val scope =
    LocalAnchors.current[S::class]
      ?: error("anchor(): no enclosing RememberAnchor provides ${S::class.simpleName}")
  return remember(scope, block) {
    { i ->
      scope.execute {
        // Safe cast: the scope was registered under S::class by RememberAnchor.
        @Suppress("UNCHECKED_CAST")
        (this as Anchor<R, S, Err>).block(i)
      }
    }
  }
}

/**
 * Creates a type-safe action callback that accepts two parameters.
 *
 * Use this for actions that need multiple values from UI events.
 *
 * Resolves the nearest enclosing [RememberAnchor] whose ViewState is `S`; actions of an outer
 * anchor called inside a nested one run on the outer anchor. Throws [IllegalStateException] if
 * no enclosing [RememberAnchor] provides `S`. Inside a [PreviewAnchor] for `S` the callback is a no-op.
 * The returned callback is referentially stable across recompositions.
 *
 * @param R The [Effect] type, inferred from the receiver of [block]
 * @param S The [ViewState] type, inferred from the receiver of [block]. Selects the Anchor to run on.
 * @param Err The domain error type, inferred from the receiver of [block]
 * @param I The type of the first parameter
 * @param O The type of the second parameter
 * @param block The action to execute. Receives the Anchor as receiver and two parameters.
 * @return A callback function that accepts two parameters
 *
 * @sample
 * ```kotlin
 * suspend fun FormAnchor.updateField(fieldId: String, value: String) {
 *   reduce { copy(fields = fields + (fieldId to value)) }
 * }
 *
 * @Composable
 * fun FormScreen() {
 *   RememberAnchor(scope = { formAnchor() }) { state ->
 *     CustomInput(
 *       onFieldChange = anchor(FormAnchor::updateField)
 *     )
 *   }
 * }
 * ```
 *
 * @see RememberAnchor For setting up the Anchor scope
 */
@Composable
public inline fun <R : Effect, reified S : ViewState, Err : Any, I, O> anchor(
  noinline block: suspend Anchor<R, S, Err>.(I, O) -> Unit,
): (I, O) -> Unit {
  val scope =
    LocalAnchors.current[S::class]
      ?: error("anchor(): no enclosing RememberAnchor provides ${S::class.simpleName}")
  return remember(scope, block) {
    { i, o ->
      scope.execute {
        // Safe cast: the scope was registered under S::class by RememberAnchor.
        @Suppress("UNCHECKED_CAST")
        (this as Anchor<R, S, Err>).block(i, o)
      }
    }
  }
}

/**
 * Creates a type-safe action callback that accepts three parameters.
 *
 * Use this for actions that need multiple values from UI events.
 *
 * Resolves the nearest enclosing [RememberAnchor] whose ViewState is `S`; actions of an outer
 * anchor called inside a nested one run on the outer anchor. Throws [IllegalStateException] if
 * no enclosing [RememberAnchor] provides `S`. Inside a [PreviewAnchor] for `S` the callback is a no-op.
 * The returned callback is referentially stable across recompositions.
 *
 * @param R The [Effect] type, inferred from the receiver of [block]
 * @param S The [ViewState] type, inferred from the receiver of [block]. Selects the Anchor to run on.
 * @param Err The domain error type, inferred from the receiver of [block]
 * @param I The type of the first parameter
 * @param O The type of the second parameter
 * @param T The type of the third parameter
 * @param block The action to execute. Receives the Anchor as receiver and three parameters.
 * @return A callback function that accepts three parameters
 *
 * @see RememberAnchor For setting up the Anchor scope
 */
@Composable
public inline fun <R : Effect, reified S : ViewState, Err : Any, I, O, T> anchor(
  noinline block: suspend Anchor<R, S, Err>.(I, O, T) -> Unit,
): (I, O, T) -> Unit {
  val scope =
    LocalAnchors.current[S::class]
      ?: error("anchor(): no enclosing RememberAnchor provides ${S::class.simpleName}")
  return remember(scope, block) {
    { i, o, t ->
      scope.execute {
        // Safe cast: the scope was registered under S::class by RememberAnchor.
        @Suppress("UNCHECKED_CAST")
        (this as Anchor<R, S, Err>).block(i, o, t)
      }
    }
  }
}
