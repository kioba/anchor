package dev.kioba.anchor.features.main.ui

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun AnchorTheme(
  isSystemDarkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = colorScheme(isSystemDarkTheme),
    content = content,
  )
}

@Composable
private fun colorScheme(
  isSystemDarkTheme: Boolean = isSystemInDarkTheme(),
): ColorScheme =
  when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicColorScheme(isSystemDarkTheme)
    isSystemDarkTheme -> darkColorScheme()
    else -> lightColorScheme()
  }

@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun dynamicColorScheme(
  darkTheme: Boolean,
): ColorScheme {
  val context = LocalContext.current
  return when {
    darkTheme -> dynamicDarkColorScheme(context)
    else -> dynamicLightColorScheme(context)
  }
}
