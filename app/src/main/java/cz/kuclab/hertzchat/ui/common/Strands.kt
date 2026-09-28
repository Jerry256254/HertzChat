package cz.kuclab.hertzchat.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Flowing audio strands (reactbits Strands style): several sine threads
 * streaming across the view. Their shape is strictly a function of the live
 * audio [level] - silence reads as a calm drift, loud input as tall waves.
 * Nothing here is random; the only motion source besides the level is a
 * constant time phase that keeps the threads streaming.
 */
@Composable
fun Strands(
    /** 0..1 audio level driving every strand's amplitude. */
    level: Float,
    color: Color,
    modifier: Modifier = Modifier,
    strandCount: Int = 5,
) {
    val transition = rememberInfiniteTransition(label = "strands")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "strands-flow",
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cy = h / 2f
        val clamped = level.coerceIn(0f, 1f)
        val stroke = Stroke(width = 2.dp.toPx())
        repeat(strandCount) { i ->
            val front = i / (strandCount - 1f).coerceAtLeast(1f)
            val amp = h * (0.05f + 0.40f * clamped) * (1f - front * 0.45f)
            val path = Path()
            val steps = 56
            for (s in 0..steps) {
                val f = s / steps.toFloat()
                val x = w * f
                // Pinched at both ends, two harmonics for an organic thread.
                val envelope = sin(PI.toFloat() * f)
                val y = cy + (
                    sin(f * 2 * PI.toFloat() * 1.5f + phase + i * 0.9f) +
                        0.35f * sin(f * 2 * PI.toFloat() * 3f - phase * 1.3f + i * 1.7f)
                    ) * amp * envelope
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color.copy(alpha = 0.95f - front * 0.55f), style = stroke)
        }
    }
}
