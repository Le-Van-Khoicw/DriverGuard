package com.example.driverguard.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = TextOnColor,
    primaryContainer = DarkSurface,
    onPrimaryContainer = DarkText,
    secondary = Safe,
    onSecondary = TextOnColor,
    tertiary = Warning,
    onTertiary = DarkBackground,
    error = Danger,
    onError = TextOnColor,
    errorContainer = DangerBackground,
    onErrorContainer = DangerText,
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkCard,
    onSurface = DarkText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkTextMuted,
    outline = DarkBorder,
    outlineVariant = DarkDivider
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = TextOnColor,
    primaryContainer = PrimaryBackground,
    onPrimaryContainer = PrimaryText,
    secondary = Safe,
    onSecondary = TextOnColor,
    tertiary = Warning,
    onTertiary = LightText,
    error = Danger,
    onError = TextOnColor,
    errorContainer = DangerBackground,
    onErrorContainer = DangerText,
    background = LightBackground,
    onBackground = LightText,
    surface = LightCard,
    onSurface = LightText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightTextMuted,
    outline = LightBorder,
    outlineVariant = LightDivider
)

@Composable
fun DriverGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Giữ false để màu thương hiệu không bị Dynamic Color của máy ghi đè.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val appColors   = if (darkTheme) darkColors()   else lightColors()

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalAppFont   provides AppFontTokens(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography  = Typography,
            content     = content
        )
    }
}
