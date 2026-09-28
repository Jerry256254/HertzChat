package cz.kuclab.hertzchat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.ripple.LocalRippleTheme
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.RippleTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val HertzBlue = Color(0xFF0B84FE)
val HertzBlueDark = Color(0xFF0662C7)
val HertzGreen = Color(0xFF25D07A)
val HertzBgLight = Color(0xFFF7F8FA)
val HertzBgDark = Color(0xFF050506)
val HertzSurfaceDark = Color(0xFF0E0E10)

private val LightColors = lightColorScheme(
    primary = HertzBlue,
    onPrimary = Color.White,
    secondary = HertzGreen,
    background = HertzBgLight,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = HertzBlue,
    onPrimary = Color.White,
    secondary = HertzGreen,
    background = HertzBgDark,
    surface = HertzSurfaceDark,
    // Near-black containers instead of Material's default greys - the dark theme
    // is black glass, not grey plastic.
    surfaceContainerLowest = Color(0xFF050506),
    surfaceContainerLow = Color(0xFF0B0B0D),
    surfaceContainer = Color(0xFF121215),
    surfaceContainerHigh = Color(0xFF17171B),
    surfaceContainerHighest = Color(0xFF1E1E23),
    surfaceVariant = Color(0xFF1A1A1F),
)

@Composable
fun HertzChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: Material You would replace our blue/green brand
    // palette with one derived from the user's wallpaper, which reads as
    // generic/washed-out rather than as Hertz Chat's own identity.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    // No ripple anywhere: the Material wash reads as a grey shadow blooming
    // over the frost. Press feedback is a breath of brightness instead, drawn
    // by the glass components themselves (see glassPressAlpha). Material3 1.2
    // still reads the M2 ripple theme, hence this import, not M3's own.
    CompositionLocalProvider(LocalRippleTheme provides NoRippleTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = HertzTypography,
            shapes = HertzThemeShapes,
            content = content,
        )
    }
}

private object NoRippleTheme : RippleTheme {
    @Composable
    override fun defaultColor() = Color.Transparent

    @Composable
    override fun rippleAlpha() = RippleAlpha(0f, 0f, 0f, 0f)
}
