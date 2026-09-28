package cz.kuclab.hertzchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The single source of truth for shape in this app: everything is round, and every
 * surface of the same kind shares the exact same radius. Nothing square anywhere.
 */
object HertzShapes {
    /** Cards, list rows, banners, menu popups. */
    val Card = RoundedCornerShape(24.dp)

    /** Dialogs and bottom sheets. */
    val Dialog = RoundedCornerShape(28.dp)

    /** Message bubbles - pure pills; side is carried by alignment and tint, not a sharp corner. */
    val BubbleMine = RoundedCornerShape(percent = 50)
    val BubbleTheirs = RoundedCornerShape(percent = 50)

    /** Fully round: input pill, chips, FABs, icon buttons, avatars. */
    val Pill = RoundedCornerShape(percent = 50)

    /** Photo/video thumbnails and their fullscreen frames. */
    val Media = RoundedCornerShape(20.dp)

    /** Small inline elements (code blocks, progress bars, popup chips). */
    val Small = RoundedCornerShape(12.dp)
}

/** Rounded defaults for every Material component that takes its shape from the theme (menus, dialogs, cards, sheets). */
val HertzThemeShapes = Shapes(
    extraSmall = RoundedCornerShape(14.dp),
    small = RoundedCornerShape(18.dp),
    medium = RoundedCornerShape(22.dp),
    large = HertzShapes.Card,
    extraLarge = HertzShapes.Dialog,
)

/**
 * Matte-translucent surfaces: near-opaque tints of the theme containers that let a
 * whisper of the background through. Deliberately *not* glassmorphism - no blur, no
 * frosted streaks, just a soft matte depth that stays readable in both themes.
 */
object HertzMatte {
    /** Default card/row surface. */
    @Composable
    fun card(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)

    /** Raised card surface (highlighted rows, assistant card, banners). */
    @Composable
    fun cardRaised(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f)

    /** Pinned-row tint. */
    @Composable
    fun pinned(): Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
}
