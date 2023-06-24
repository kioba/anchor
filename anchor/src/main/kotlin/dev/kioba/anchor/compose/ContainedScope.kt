package dev.kioba.anchor.compose

import dev.kioba.anchor.AnchorScope
import kotlinx.coroutines.CoroutineScope

@PublishedApi
internal interface ContainedScope<R, S, E> where R : AnchorScope<S, E> {
  val anchorScope: R
  val coroutineScope: CoroutineScope
  val actionChannel: AnchorChannel
}
