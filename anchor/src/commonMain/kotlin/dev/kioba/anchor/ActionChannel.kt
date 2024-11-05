package dev.kioba.anchor

@PublishedApi
internal fun interface ActionChannel {
  fun execute(
    capture: AnchorOf<out Anchor<*, *>>,
  )
}

public fun interface AnchorOf<A> {
  public suspend fun A.execute()
}

@AnchorDsl
public fun <E, S> anchorScope(
  block: suspend Anchor<E, S>.() -> Unit,
): AnchorOf<Anchor<E, S>> where E : Effect, S : ViewState =
  AnchorOf(block)
