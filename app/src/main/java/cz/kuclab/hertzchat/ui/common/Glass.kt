package cz.kuclab.hertzchat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.kuclab.hertzchat.ui.theme.HertzShapes

/**
 * The app's glassmorphism language: every floating surface (bars, bubbles, rows,
 * input, dialogs) is the same material - a translucent fill, a hairline bright
 * edge, and a short light streak across the top where the light catches it. One
 * material everywhere is what makes the design read as one piece. (Anchored
 * popup menus stay opaque on purpose - translucent menus let the rows behind
 * collide with the item text.)
 */
object HertzGlass {
    /** Default translucent fill for bars, rows and bubbles-theirs. */
    @Composable
    fun fill(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.13f) else Color.White.copy(alpha = 0.60f)

    /** Heavier fill for bars floating over scrolling content. */
    @Composable
    fun fillStrong(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.78f)

    /** Opaque container for anchored popup menus - translucent menus let the rows behind collide with the item text. */
    @Composable
    fun menuSolid(): Color = if (isSystemInDarkTheme()) Color(0xFF202028) else Color(0xFFEFF1F5)

    /** Hairline edge around every glass surface. */
    @Composable
    fun stroke(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.20f) else Color.Black.copy(alpha = 0.10f)

    /** The bright streak across a surface's top edge. */
    @Composable
    fun streak(): Color = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.38f) else Color.White.copy(alpha = 0.95f)

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
 * The hairline edge plus the top light streak. Drawn as modifiers so any
 * container (Box, Surface, button) becomes glass with the same two lines.
 */
fun Modifier.glassEdge(shape: Shape): Modifier = composed {
    val stroke = HertzGlass.stroke()
    val streak = HertzGlass.streak()
    val density = LocalDensity.current
    border(1.dp, stroke, shape)
        .drawBehind {
            // A centered streak, not a full top line: reads as light catching
            // curved glass rather than a flat rule. Width-relative so pills,
            // circles and cards all get the same treatment with no tuning.
            val y = with(density) { 2.dp.toPx() }
            val fromX = size.width * 0.28f
            val toX = size.width * 0.72f
            if (toX - fromX > with(density) { 8.dp.toPx() }) {
                drawLine(
                    color = streak,
                    start = Offset(fromX, y),
                    end = Offset(toX, y),
                    strokeWidth = with(density) { 1.5.dp.toPx() },
                    cap = StrokeCap.Round,
                )
            }
        }
}

/**
 * One glass panel: tinted shadow, translucent fill, hairline edge, top streak.
 * Pass [shadowElevation] = 0.dp inside scrolling lists - per-item shadows cost
 * an offscreen pass each, the edge and streak alone carry the glass there.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = HertzShapes.Card,
    fill: Color = HertzGlass.fill(),
    shadowElevation: Dp = 12.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val clickableMod = if (onClick != null) {
        Modifier.clip(shape).clickable(onClick = onClick)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .shadow(shadowElevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.30f), spotColor = Color.Black.copy(alpha = 0.45f))
            .clip(shape)
            .background(fill)
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
    modifier: Modifier = Modifier,
) {
    val fill = when {
        danger -> MaterialTheme.colorScheme.error
        accent -> MaterialTheme.colorScheme.primary
        else -> HertzGlass.fill()
    }
    val iconTint = when {
        danger -> MaterialTheme.colorScheme.onError
        accent -> MaterialTheme.colorScheme.onPrimary
        else -> HertzGlass.contentOnGlass()
    }
    // Press feedback is the bounded ripple only - icons and buttons are never
    // scaled or otherwise deformed.
    Box(
        modifier = modifier
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
 * The backdrop the glass floats over: the theme background with three soft
 * ambient color glows. Procedural, so it can never misalign or tile visibly -
 * and dark enough that every glass edge reads. Always fills its parent: a
 * zero-size canvas would silently render nothing at all.
 */
@Composable
fun GlassAmbientBackground(modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.background
    val dark = isSystemInDarkTheme()
    val teal = Color(0xFF2DD4BF).copy(alpha = if (dark) 0.20f else 0.14f)
    val indigo = Color(0xFF5B7CFF).copy(alpha = if (dark) 0.24f else 0.16f)
    val violet = Color(0xFFA78BFA).copy(alpha = if (dark) 0.12f else 0.08f)
    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize().background(base)) {
        fun glow(color: Color, cx: Float, cy: Float, radius: Float) {
            drawCircle(
                brush = Brush.radialGradient(listOf(color, Color.Transparent), center = Offset(cx, cy), radius = radius),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
        val r = size.minDimension * 1.1f
        glow(teal, size.width * 0.12f, size.height * 0.08f, r)
        glow(indigo, size.width * 0.92f, size.height * 0.96f, r)
        glow(violet, size.width * 0.88f, size.height * 0.22f, r * 0.7f)
    }
}

/**
 * A gradient veil drawn behind the floating top bars: scrolling messages fade
 * out underneath the chrome instead of colliding with it through the glass.
 * Drawn (not clickable) between the scrolling content and the bars.
 */
@Composable
fun TopBarScrim(modifier: Modifier = Modifier, height: Dp = 150.dp) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    listOf(
                        bg.copy(alpha = 0.92f),
                        bg.copy(alpha = 0.55f),
                        Color.Transparent,
                    ),
                ),
            ),
    )
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
