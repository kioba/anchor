package dev.kioba.anchor.features.main.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorPalette = darkColors(
  primary = Teal200,
  primaryVariant = Purple700,
  secondary = Purple200
)

private val LightColorPalette = lightColors(
  primary = Teal200,
  primaryVariant = Purple700,
  secondary = Purple500

  /* Other default colors to override
  background = Color.White,
  surface = Color.White,
  onPrimary = Color.White,
  onSecondary = Color.Black,
  onSurface = Color.Black,
  */
)

@Composable
internal fun AnchorAppTheme(
  isSystemDarkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colors = when {
    isSystemDarkTheme -> DarkColorPalette
    else -> LightColorPalette
  }

  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = (colors.primarySurface).toArgb()
      window.navigationBarColor = (colors.primarySurface).toArgb()

      WindowCompat.getInsetsController(window, view)
        .isAppearanceLightStatusBars = !isSystemDarkTheme
      WindowCompat.getInsetsController(window, view)
        .isAppearanceLightNavigationBars = !isSystemDarkTheme
    }
  }

  MaterialTheme(
    colors = colors,
    typography = Typography,
    shapes = Shapes,
    content = content
  )
}