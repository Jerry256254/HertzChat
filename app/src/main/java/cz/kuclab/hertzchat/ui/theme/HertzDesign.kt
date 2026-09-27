package cz.kuclab.hertzchat.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
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

    /** Message bubbles - the small corner marks which side the message sits on. */
    val BubbleMine = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    val BubbleTheirs = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)

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

    /** Chat input pill and search fields. */
    @Composable
    fun input(): Color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f)

    /** Pinned-row tint. */
    @Composable
    fun pinned(): Color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)

    /** Top app bars - the same matte tone on every screen. */
    @Composable
    fun bar(): Color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)

    /** Incoming message bubbles. */
    @Composable
    fun bubbleTheirs(): Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f)

    /** Shared palette for every CenterAlignedTopAppBar so bars never drift apart again. */
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun topBarColors(): TopAppBarColors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = bar(),
        scrolledContainerColor = bar(),
    )
}
