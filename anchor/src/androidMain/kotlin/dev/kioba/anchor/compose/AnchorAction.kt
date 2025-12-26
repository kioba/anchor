package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import dev.kioba.anchor.Anchor
import dev.kioba.anchor.Effect
import dev.kioba.anchor.ViewState

/**
 * Creates a type-safe action callback that executes on the current Anchor instance.
 *
 * Use this within a [RememberAnchor] scope to convert Anchor actions into callbacks that
 * can be passed to UI event handlers like `onClick`, `onValueChange`, etc.
 *
 * The Anchor type is automatically inferred from the action function's receiver type,
 * providing compile-time type safety.
 *
 * @param A The Anchor type, automatically inferred from the block parameter
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
public fun <A> anchor(
  block: suspend A.() -> Unit,
): () -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val scope = LocalAnchor.current
  return {
    // Safe cast: A is constrained to be an Anchor subtype, and scope is provided
    // by the nearest RememberAnchor which guarantees the correct anchor type
    @Suppress("UNCHECKED_CAST")
    scope.execute(block as suspend Anchor<*, *>.() -> Unit)
  }
}

/**
 * Creates a type-safe action callback that accepts one parameter.
 *
 * Use this when your action needs to receive a value from a UI event, such as text input,
 * slider values, or item selections.
 *
 * @param A The Anchor type, automatically inferred from the block parameter
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
public fun <A, I> anchor(
  block: suspend A.(I) -> Unit,
): (I) -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val scope = LocalAnchor.current
  return { i ->
    @Suppress("UNCHECKED_CAST")
    scope.execute { (this as A).block(i) }
  }
}

/**
 * Creates a type-safe action callback that accepts two parameters.
 *
 * Use this for actions that need multiple values from UI events.
 *
 * @param A The Anchor type, automatically inferred from the block parameter
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
public fun <A, I, O> anchor(
  block: suspend A.(I, O) -> Unit,
): (I, O) -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val scope = LocalAnchor.current
  return { i, o ->
    @Suppress("UNCHECKED_CAST")
    scope.execute { (this as A).block(i, o) }
  }
}

/**
 * Creates a type-safe action callback that accepts three parameters.
 *
 * Use this for actions that need multiple values from UI events.
 *
 * @param A The Anchor type, automatically inferred from the block parameter
 * @param I The type of the first parameter
 * @param O The type of the second parameter
 * @param T The type of the third parameter
 * @param block The action to execute. Receives the Anchor as receiver and three parameters.
 * @return A callback function that accepts three parameters
 *
 * @see RememberAnchor For setting up the Anchor scope
 */
@Composable
public fun <A, I, O, T> anchor(
  block: suspend A.(I, O, T) -> Unit,
): (I, O, T) -> Unit
  where A : Anchor<out Effect, out ViewState> {
  val scope = LocalAnchor.current
  return { i, o, t ->
    @Suppress("UNCHECKED_CAST")
    scope.execute { (this as A).block(i, o, t) }
  }
}
