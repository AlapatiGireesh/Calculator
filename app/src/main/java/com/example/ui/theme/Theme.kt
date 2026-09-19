package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFFD3E4FF),
    onPrimary = Color(0xFF001D35),
    secondary = Color(0xFFD2E3FC),
    onSecondary = Color(0xFF001D35),
    primaryContainer = Color(0xFF001D35),
    onPrimaryContainer = Color(0xFFD3E4FF),
    background = Color(0xFF0B0F13),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF13181E),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF1E242C),
    onSurfaceVariant = Color(0xFFC4C7C5),
    outline = Color(0xFF2E353F)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = MinimalistPrimary,
    onPrimary = MinimalistOnPrimary,
    secondary = MinimalistSecondary,
    onSecondary = MinimalistOnSecondary,
    primaryContainer = MinimalistPrimaryContainer,
    onPrimaryContainer = MinimalistOnPrimaryContainer,
    background = MinimalistBackground,
    onBackground = MinimalistOnBackground,
    surface = MinimalistSurface,
    onSurface = MinimalistOnSurface,
    surfaceVariant = MinimalistSurfaceVariant,
    onSurfaceVariant = MinimalistOnSurfaceVariant,
    outline = MinimalistBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is disabled by default to preserve the hand-coded Clean Minimalism branding requested by the user
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
