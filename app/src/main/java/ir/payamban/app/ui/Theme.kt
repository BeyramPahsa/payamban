package ir.payamban.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val Teal = Color(0xFF0F5C4E)
private val TealLight = Color(0xFF4E9C8A)
private val Amber = Color(0xFFB26A00)

private val LightScheme = lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EDE6),
    onPrimaryContainer = Color(0xFF05261F),
    secondary = Amber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6C4),
    onSecondaryContainer = Color(0xFF3A2200),
    background = Color(0xFFF7F9F8),
    onBackground = Color(0xFF15201D),
    surface = Color.White,
    onSurface = Color(0xFF15201D),
    surfaceVariant = Color(0xFFE6EDEA),
    onSurfaceVariant = Color(0xFF44514D),
    error = Color(0xFFB3261E)
)

private val DarkScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF04241E),
    primaryContainer = Color(0xFF14463C),
    onPrimaryContainer = Color(0xFFD6EDE6),
    secondary = Color(0xFFF0B860),
    onSecondary = Color(0xFF3A2200),
    background = Color(0xFF101513),
    onBackground = Color(0xFFE3E8E6),
    surface = Color(0xFF171E1C),
    onSurface = Color(0xFFE3E8E6),
    surfaceVariant = Color(0xFF2A3330),
    onSurfaceVariant = Color(0xFFBFC9C5)
)

/** کل رابط کاربری راست‌به‌چپ است. */
@Composable
fun PayamBanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            content()
        }
    }
}
