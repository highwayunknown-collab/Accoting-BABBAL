package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

private val BabbalLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = OnEmeraldContainer,
    secondary = CharcoalSecondary,
    onSecondary = Color.White,
    secondaryContainer = CharcoalContainer,
    onSecondaryContainer = OnCharcoalContainer,
    tertiary = GvvsAccent,
    onTertiary = Color.White,
    background = SoftOffWhiteBg,
    onBackground = CharcoalSecondary,
    surface = PureWhiteSurface,
    onSurface = CharcoalSecondary,
    surfaceVariant = SubtleSurfaceVariant,
    onSurfaceVariant = Color(0xFF4F5D57),
    outline = SubtleOutline,
    outlineVariant = Color(0xFFE6ECE9),
    error = ExpenseRed,
    onError = Color.White,
    errorContainer = ExpenseRedLight,
    onErrorContainer = Color(0xFF7F1D1D)
)

private val BabbalDarkColorScheme = darkColorScheme(
    primary = EmeraldLightAccent,
    onPrimary = Color(0xFF04291C),
    primaryContainer = EmeraldDarkContainer,
    onPrimaryContainer = Color(0xFFB3F1D5),
    secondary = Color(0xFFA7B8B1),
    onSecondary = Color(0xFF17201D),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = Color(0xFFD5E1DC),
    tertiary = Color(0xFF4FD1C5),
    onTertiary = Color(0xFF042F2E),
    background = DarkBg,
    onBackground = Color(0xFFEAF1EE),
    surface = DarkSurface,
    onSurface = Color(0xFFEAF1EE),
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFF9FB0A9),
    outline = DarkOutline,
    outlineVariant = Color(0xFF24322D),
    error = ExpenseRedDark,
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFECACA)
)

val BabbalShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun BabbalTheme(
    themeMode: ThemeMode = ThemeMode.LIGHT,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) BabbalDarkColorScheme else BabbalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BabbalTypography,
        shapes = BabbalShapes,
        content = content
    )
}
