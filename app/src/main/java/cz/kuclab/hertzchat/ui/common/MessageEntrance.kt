package cz.kuclab.hertzchat.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Entrance for a chat message that landed while the thread is open: a quick
 * fade with a short rise, plus a whisper of scale. History renders directly -
 * replaying entrances on scroll-back would be motion noise, not feedback.
 */
@Composable
fun MessageEntrance(isNew: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (!isNew) {
        content()
        return
    }
    // Starts invisible and targets visible, so the enter transition plays once
    // on composition instead of rendering the finished state.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 3 } + scaleIn(tween(180), initialScale = 0.97f),
        modifier = modifier,
    ) {
        content()
    }
}
