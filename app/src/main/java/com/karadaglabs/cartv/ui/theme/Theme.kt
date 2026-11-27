package com.karadaglabs.cartv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * KaradagCarTV tasarım sistemi: tek, koyu ÖK teması (turuncu + antrasit).
 * Araç ekranında yüksek kontrast için dinamik renk kullanılmaz; telefon ve
 * araç aynı renkleri paylaşır, yerleşim/ölçü [CarScreen] ile ayrışır.
 */
private val OkColorScheme = darkColorScheme(
    primary = OkColors.Orange,
    onPrimary = OkColors.Anthracite,
    primaryContainer = OkColors.OrangeDim,
    onPrimaryContainer = OkColors.Ink,
    secondary = OkColors.InkMuted,
    onSecondary = OkColors.Anthracite,
    tertiary = OkColors.Orange,
    background = OkColors.Anthracite,
    onBackground = OkColors.Ink,
    surface = OkColors.AnthraciteLight,
    onSurface = OkColors.Ink,
    surfaceVariant = OkColors.Surface,
    onSurfaceVariant = OkColors.InkMuted,
    surfaceContainer = OkColors.Surface,
    surfaceContainerHigh = OkColors.SurfaceHigh,
    outline = OkColors.Outline,
    error = OkColors.Error,
    onError = OkColors.Anthracite
)

@Composable
fun KaradagCarTVTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    @Suppress("UNUSED_PARAMETER") dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OkColorScheme,
        typography = Typography,
        content = content
    )
}
