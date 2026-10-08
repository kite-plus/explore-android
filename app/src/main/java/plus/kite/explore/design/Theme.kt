package plus.kite.explore.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The Kite Plus blue the app is tinted with, as on iOS and the website. */
val KiteBlue = Color(0xFF4A77D6)
val KiteBlueDark = Color(0xFF8FB0F2)

/**
 * The app's colors, named as the iOS app uses them, so both keep one look: white lists,
 * gray grouped screens, black or white main actions and the blue only for accents.
 */
@Immutable
data class ExploreColors(
    val background: Color,
    val groupedBackground: Color,
    val card: Color,
    val secondaryBackground: Color,
    val fill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val quaternaryLabel: Color,
    val separator: Color,
    val accent: Color,
    val orange: Color,
    val green: Color,
    val red: Color,
    val teal: Color,
    val isDark: Boolean,
)

private val LightColors = ExploreColors(
    background = Color.White,
    groupedBackground = Color(0xFFF2F2F7),
    card = Color.White,
    secondaryBackground = Color(0xFFF2F2F7),
    fill = Color(0x1F767680),
    label = Color.Black,
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    quaternaryLabel = Color(0x2E3C3C43),
    separator = Color(0x333C3C43),
    accent = KiteBlue,
    orange = Color(0xFFFF9500),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    teal = Color(0xFF30B0C7),
    isDark = false,
)

private val DarkColors = ExploreColors(
    background = Color.Black,
    groupedBackground = Color.Black,
    card = Color(0xFF1C1C1E),
    secondaryBackground = Color(0xFF1C1C1E),
    fill = Color(0x3D767680),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    quaternaryLabel = Color(0x29EBEBF5),
    separator = Color(0x66545458),
    accent = KiteBlueDark,
    orange = Color(0xFFFF9F0A),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    teal = Color(0xFF40C8E0),
    isDark = true,
)

val LocalExploreColors = staticCompositionLocalOf { LightColors }

/** Text sizes as the iOS app's text styles have them. */
object ExploreType {
    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold)
    val title = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 23.sp)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp)
}

object Explore {
    val colors: ExploreColors
        @Composable @ReadOnlyComposable get() = LocalExploreColors.current
}

@Composable
fun ExploreTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = KiteBlueDark,
            onPrimary = Color.Black,
            background = colors.background,
            onBackground = colors.label,
            surface = colors.background,
            onSurface = colors.label,
            onSurfaceVariant = colors.secondaryLabel,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF111113),
            surfaceContainer = colors.card,
            surfaceContainerHigh = Color(0xFF242426),
            surfaceContainerHighest = Color(0xFF2C2C2E),
            outline = colors.tertiaryLabel,
            outlineVariant = colors.separator,
            secondaryContainer = Color(0xFF2C2C2E),
            onSecondaryContainer = colors.label,
        )
    } else {
        lightColorScheme(
            primary = KiteBlue,
            onPrimary = Color.White,
            background = colors.background,
            onBackground = colors.label,
            surface = colors.background,
            onSurface = colors.label,
            onSurfaceVariant = colors.secondaryLabel,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF7F7FA),
            surfaceContainer = Color(0xFFF2F2F7),
            surfaceContainerHigh = Color(0xFFECECF1),
            surfaceContainerHighest = Color(0xFFE5E5EA),
            outline = colors.tertiaryLabel,
            outlineVariant = colors.separator,
            secondaryContainer = Color(0xFFE5E5EA),
            onSecondaryContainer = colors.label,
        )
    }
    val typography = Typography(
        bodyLarge = ExploreType.body,
        bodyMedium = ExploreType.subheadline,
        bodySmall = ExploreType.footnote,
        titleLarge = ExploreType.title3,
        titleMedium = ExploreType.headline,
        titleSmall = ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = ExploreType.subheadline.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = ExploreType.caption.copy(fontWeight = FontWeight.Medium),
        labelSmall = ExploreType.caption2.copy(fontWeight = FontWeight.Medium),
        headlineSmall = ExploreType.title2,
        headlineMedium = ExploreType.title,
        headlineLarge = ExploreType.largeTitle,
    )
    CompositionLocalProvider(LocalExploreColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
