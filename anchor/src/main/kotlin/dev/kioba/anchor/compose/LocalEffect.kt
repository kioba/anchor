package dev.kioba.anchor.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import dev.kioba.anchor.UnitCommand
import dev.kioba.anchor.AnchorCommand

@PublishedApi
internal fun interface CommandProvider {
  fun provide(): AnchorCommand
}

@PublishedApi
internal val LocalCommand: ProvidableCompositionLocal<CommandProvider> =
  staticCompositionLocalOf { CommandProvider { UnitCommand } }

@Composable
public inline fun <reified T> HandleCommand(
  noinline f: suspend (T) -> Unit,
) {
  val effectProvider = LocalCommand.current
  when (val effect = effectProvider.provide()) {
    is UnitCommand -> Unit
    is T -> LaunchedEffect(effectProvider) { f(effect) }
  }
}