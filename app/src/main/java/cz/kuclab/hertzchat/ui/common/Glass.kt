package cz.kuclab.hertzchat.ui.common

import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeChild

/**
 * The app's glass language: every floating surface (bars, bubbles, rows,
 * input, menus, dialogs) is the same material - a translucent fill over
 * blurred content behind it, plus a hairline edge. No gradients, no light
 * streaks: the blur itself is the decoration.
 *
 * Two blur mechanisms, because Android gives no single one:
 * - in-layout chrome (the floating top bars over scrolling lists) blurs via
 *   Haze: the screen puts [dev.chrisbanes.haze.haze] on its scrolling content
 *   and passes the [HazeState] down to the bars, which register with
 *   [hazeChild]. Real blur on Android 12L+, a matching tint scrim below.
 * - popup menus, dialogs and bottom sheets live in their own windows, where
 *   Haze can't reach, so they blur with the platform blur-behind instead
 *   (see [WindowBlurBehind], Android 12+) over a translucent fill.
 */
object HertzGlass {
    /** Default translucent fill for bars, rows and bubbles-theirs. */
    @Composable
    fun fill(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.60f)

    /**
     * Translucent container for anchored popup menus. The blur behind comes
     * from [WindowBlurBehind], which only exists on Android 12+ - below that
     * the menu stays near-opaque so the rows behind never collide with the
     * item text.
     */
    @Composable
    fun menuFill(): Color {
        val dark = isSystemInDarkTheme()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) Color(0xFF202028).copy(alpha = 0.72f) else Color(0xFFEFF1F5).copy(alpha = 0.72f)
        } else {
            if (dark) Color(0xFF202028).copy(alpha = 0.96f) else Color(0xFFEFF1F5).copy(alpha = 0.97f)
        }
    }

    /** Hairline edge around every glass surface. */
    @Composable
    fun stroke(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f)

    /**
     * The frosted tint Haze paints over blurred content in the glass areas.
     * It doubles as the whole fill on older Androids (where Haze draws this
     * tint as a scrim instead of blurring), so it must stay readable on its
     * own - the same tone as [fill], a touch stronger for text contrast.
     */
    @Composable
    fun hazeTint(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.62f)

    /** The one Haze style every blurred bar in the app shares. */
    @Composable
    fun hazeStyle(): HazeStyle = HazeStyle(tint = hazeTint(), blurRadius = 28.dp, noiseFactor = 0.08f)

    /** Own-message bubbles: accent-tinted glass, readable in both themes. */
    @Composable
    fun bubbleMine(): Color = MaterialTheme.colorScheme.primary.copy(alpha = if (isSystemInDarkTheme()) 0.45f else 0.85f)

    /** Other people's bubbles: neutral glass. */
    @Composable
    fun bubbleTheirs(): Color = fill()

    /** Main text on glass - pure contrast, slightly heavy (vibrancy). */
    @Composable
    fun contentOnGlass(): Color = if (isSystemInDarkTheme()) Color.White else MaterialTheme.colorScheme.onSurface
}

/**
 * The hairline edge around a glass surface. Drawn as a modifier so any
 * container (Box, Surface, button) becomes glass with the same line.
 */
fun Modifier.glassEdge(shape: Shape): Modifier = composed {
    border(1.dp, HertzGlass.stroke(), shape)
}

/**
 * One glass panel: tinted shadow, translucent fill, hairline edge. Pass a
 * [hazeState] for chrome that floats over scrolling content (the top bars) -
 * the fill then goes transparent and Haze paints blurred, tinted content
 * behind the panel instead; the shadow, edge and content stay untouched.
 * Everything that sits on the flat background (bubbles, rows, cards, the
 * input pill) keeps its plain translucent fill - there is nothing behind it
 * worth blurring. Pass [shadowElevation] = 0.dp inside scrolling lists -
 * per-item shadows cost an offscreen pass each, the edge alone carries the
 * glass there.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HertzShapes.Card,
    fill: Color = HertzGlass.fill(),
    shadowElevation: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    hazeState: HazeState? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickableMod = if (onClick != null) {
        Modifier.clip(shape).clickable(onClick = onClick)
    } else {
        Modifier
    }
    val hazeMod = if (hazeState != null) Modifier.hazeChild(hazeState, shape) else Modifier
    Box(
        modifier = modifier
            .then(hazeMod)
            .shadow(shadowElevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(if (hazeState != null) Color.Transparent else fill)
            .then(clickableMod)
            .glassEdge(shape),
        content = content,
    )
}

/** Round glass icon button for bars and menus (back, options, settings, mic). */
@Composable
fun GlassCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    /** Solid accent fill for the primary action; glass otherwise. */
    accent: Boolean = false,
    /** Solid red fill for destructive/recording states. */
    danger: Boolean = false,
    /** Blur behind this button - same deal as [GlassSurface]; ignored for solid accent/danger fills. */
    hazeState: HazeState? = null,
    modifier: Modifier = Modifier,
) {
    val blurred = hazeState != null && !accent && !danger
    val fill = when {
        danger -> MaterialTheme.colorScheme.error
        accent -> MaterialTheme.colorScheme.primary
        blurred -> Color.Transparent
        else -> HertzGlass.fill()
    }
    val iconTint = when {
        danger -> MaterialTheme.colorScheme.onError
        accent -> MaterialTheme.colorScheme.onPrimary
        else -> HertzGlass.contentOnGlass()
    }
    val hazeMod = if (blurred) Modifier.hazeChild(hazeState!!, CircleShape) else Modifier
    // Press feedback is the bounded ripple only - icons and buttons are never
    // scaled or otherwise deformed.
    Box(
        modifier = modifier
            .then(hazeMod)
            .size(size)
            .shadow(8.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.40f))
            .clip(CircleShape)
            .background(fill)
            .clickable(onClick = onClick)
            .glassEdge(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = iconTint, modifier = Modifier.size(22.dp))
    }
}

/**
 * The backdrop the glass floats over: the flat theme background. Deliberately
 * no gradients or color glows - the frosted blur of the floating chrome is
 * the only depth cue, and it needs a calm surface to read against. Always
 * fills its parent: a zero-size canvas would silently render nothing at all.
 */
@Composable
fun GlassAmbientBackground(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
}

/**
 * Blurs whatever sits behind this window - the frosted backdrop for popup
 * menus, dialogs and bottom sheets, which live in their own windows where
 * Haze can't reach. Must be called from *inside* the popup/dialog/sheet
 * content (so [LocalView] belongs to that window); anywhere else it would
 * find the activity window instead. Android 12+ only - below that it renders
 * nothing and the translucent fills carry the look on their own.
 */
@Composable
fun WindowBlurBehind(radius: Dp = 32.dp) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val view = LocalView.current
    val density = LocalDensity.current
    SideEffect {
        runCatching {
            applyWindowBlurBehind(view, with(density) { radius.roundToPx() })
        }
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun applyWindowBlurBehind(view: android.view.View, radiusPx: Int) {
    // The ComposeView inside a popup/dialog/sheet is nested a few Views deep;
    // the window's own view is the first ancestor whose layout params are
    // WindowManager params (PopupLayout for popups, DecorView for dialogs).
    var parent = view.parent
    while (parent != null) {
        val params = (parent as? android.view.View)?.layoutParams
        if (params is WindowManager.LayoutParams) {
            if (params.blurBehindRadius != radiusPx) {
                params.blurBehindRadius = radiusPx
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                (view.context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .updateViewLayout(parent as android.view.View, params)
            }
            return
        }
        parent = parent.parent
    }
}

/** Glass-tinted container for the modal confirmation dialogs, so they sit in the same material family as the menus. */
@Composable
fun GlassDialogTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val container = if (dark) Color(0xFF1E1E26) else Color(0xFFF2F4F8)
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(surfaceContainerHigh = container),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        content = content,
    )
}
