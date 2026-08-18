package com.smartaodi.dshandroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B55),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF9FF3D5),
    onPrimaryContainer = Color(0xFF002119),
    secondary = Color(0xFF4D635B),
    secondaryContainer = Color(0xFFD0E8DE),
    onSecondaryContainer = Color(0xFF0A1F19),
    tertiary = Color(0xFF8A5B15),
    tertiaryContainer = Color(0xFFFFEBCB),
    onTertiaryContainer = Color(0xFF55370B),
    background = Color(0xFFF6F9F7),
    onBackground = Color(0xFF181D1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF181D1A),
    surfaceVariant = Color(0xFFDDE5E1),
    onSurfaceVariant = Color(0xFF5C665F),
    outline = Color(0xFF87918B),
    outlineVariant = Color(0xFFD7E0DB),
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55E0B5),
    onPrimary = Color(0xFF00382B),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFF79F8CD),
    secondary = Color(0xFFB4CCC2),
    secondaryContainer = Color(0xFF354B43),
    onSecondaryContainer = Color(0xFFD0E8DE),
    tertiary = Color(0xFFFFB94E),
    tertiaryContainer = Color(0xFF5D410C),
    onTertiaryContainer = Color(0xFFFFE2B4),
    background = Color(0xFF0E1210),
    onBackground = Color(0xFFE0E5E1),
    surface = Color(0xFF151A17),
    onSurface = Color(0xFFE0E5E1),
    surfaceVariant = Color(0xFF3F4944),
    onSurfaceVariant = Color(0xFFBFC9C3),
    outline = Color(0xFF89938D),
    outlineVariant = Color(0xFF39423D),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
)

private val HarnessTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    ),
)

@Composable
fun DshAndroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = HarnessTypography,
        content = content,
    )
}
