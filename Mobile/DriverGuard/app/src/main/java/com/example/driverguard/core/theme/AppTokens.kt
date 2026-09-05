package com.example.driverguard.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────
// Font tokens  →  font.*
// ─────────────────────────────────────────────
@Immutable
data class AppFontTokens(
    // Sizes
    val xs: TextUnit     = 11.sp,
    val sm: TextUnit     = 13.sp,
    val base: TextUnit   = 15.sp,
    val md: TextUnit     = 17.sp,
    val lg: TextUnit     = 20.sp,
    val xl: TextUnit     = 24.sp,
    val xxl: TextUnit    = 30.sp,
    // Weights
    val regular: FontWeight  = FontWeight.Normal,
    val medium: FontWeight   = FontWeight.Medium,
    val semibold: FontWeight = FontWeight.SemiBold,
    val bold: FontWeight     = FontWeight.Bold,
    val black: FontWeight    = FontWeight.Black,
)

val LocalAppFont = staticCompositionLocalOf { AppFontTokens() }

// ─────────────────────────────────────────────
// Color tokens  →  c.*
// ─────────────────────────────────────────────
@Immutable
data class AppColorTokens(
    // Background
    val bg: Color,
    val card: Color,
    val surface: Color,
    val inputBg: Color,
    val divider: Color,
    val border: Color,
    // Text
    val text: Color,
    val textMuted: Color,
    val textSubtle: Color,
    val textOnColor: Color,
    // Chrome
    val appBar: Color,
    val bottomNav: Color,
    val statusBar: Color,
    // Primary
    val primary: Color,
    val primaryBg: Color,
    val primaryText: Color,
    // Safe
    val safe: Color,
    val safeBg: Color,
    val safeText: Color,
    // Warning
    val warning: Color,
    val warningBg: Color,
    val warningText: Color,
    // Danger
    val danger: Color,
    val dangerBg: Color,
    val dangerText: Color,
)

fun lightColors() = AppColorTokens(
    bg          = LightBackground,
    card        = LightCard,
    surface     = LightSurface,
    inputBg     = LightInputBackground,
    divider     = LightDivider,
    border      = LightBorder,
    text        = LightText,
    textMuted   = LightTextMuted,
    textSubtle  = LightTextSubtle,
    textOnColor = TextOnColor,
    appBar      = LightAppBar,
    bottomNav   = LightBottomNav,
    statusBar   = LightStatusBar,
    primary     = Primary,
    primaryBg   = PrimaryBackground,
    primaryText = PrimaryText,
    safe        = Safe,
    safeBg      = SafeBackground,
    safeText    = SafeText,
    warning     = Warning,
    warningBg   = WarningBackground,
    warningText = WarningText,
    danger      = Danger,
    dangerBg    = DangerBackground,
    dangerText  = DangerText,
)

fun darkColors() = AppColorTokens(
    bg          = DarkBackground,
    card        = DarkCard,
    surface     = DarkSurface,
    inputBg     = DarkInputBackground,
    divider     = DarkDivider,
    border      = DarkBorder,
    text        = DarkText,
    textMuted   = DarkTextMuted,
    textSubtle  = DarkTextSubtle,
    textOnColor = TextOnColor,
    appBar      = DarkAppBar,
    bottomNav   = DarkBottomNav,
    statusBar   = DarkStatusBar,
    primary     = Primary,
    primaryBg   = PrimaryBackground,
    primaryText = PrimaryText,
    safe        = Safe,
    safeBg      = SafeBackground,
    safeText    = SafeText,
    warning     = Warning,
    warningBg   = WarningBackground,
    warningText = WarningText,
    danger      = Danger,
    dangerBg    = DangerBackground,
    dangerText  = DangerText,
)

val LocalAppColors = staticCompositionLocalOf { lightColors() }

// ─────────────────────────────────────────────
// Extension shortcuts:
//   val c    = MaterialTheme.c
//   val font = MaterialTheme.font
// ─────────────────────────────────────────────
val MaterialTheme.c: AppColorTokens
    @Composable @ReadOnlyComposable get() = LocalAppColors.current

val MaterialTheme.font: AppFontTokens
    @Composable @ReadOnlyComposable get() = LocalAppFont.current
