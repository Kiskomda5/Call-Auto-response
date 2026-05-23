package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = YellowPrimary,
    onPrimary = DarkGrey,
    background = DarkGrey,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = DarkGreySurface,
    onSurface = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF404040),
    onPrimaryContainer = YellowPrimary,
    outline = androidx.compose.ui.graphics.Color.LightGray,
    outlineVariant = androidx.compose.ui.graphics.Color.DarkGray
  )

private val LightColorScheme =
  lightColorScheme(
    primary = RedPrimary,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = androidx.compose.ui.graphics.Color.White,
    onBackground = androidx.compose.ui.graphics.Color.Black,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color.Black,
    primaryContainer = RedLight,
    onPrimaryContainer = RedPrimary,
    outline = androidx.compose.ui.graphics.Color.DarkGray,
    outlineVariant = androidx.compose.ui.graphics.Color.LightGray
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
