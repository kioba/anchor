package dev.kioba.anchor

@PublishedApi
internal fun interface ActionChannel {
  fun execute(
    capture: Action<out Anchor<*, *>>,
  )
}

public fun interface Action<E> {
  public suspend fun E.execute()
}
