package dev.kioba.anchor
@PublishedApi
internal fun interface AnchorChannel {
  fun execute(anchor: Anchor<out AnchorDslScope>)
}
