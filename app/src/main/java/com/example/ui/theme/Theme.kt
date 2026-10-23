package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AluDarkAccent,
    onPrimary = AluPrimaryVariant,
    secondary = AluDarkPrimary,
    onSecondary = Color.White,
    tertiary = AluSuccess,
    background = AluDarkBackground,
    surface = AluDarkSurface,
    surfaceVariant = AluDarkSurfaceVariant,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9),
)

private val LightColorScheme = lightColorScheme(
    primary = AluPrimary,
    onPrimary = Color.White,
    secondary = AluSecondary,
    onSecondary = Color.White,
    tertiary = AluAccent,
    background = AluBackground,
    surface = AluSurface,
    surfaceVariant = AluSurfaceVariant,
    onBackground = AluTextPrimary,
    onSurface = AluTextPrimary,
    outline = AluCardBorder
)

@Composable
fun TokoYogaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Preserve branded aluminium look
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
