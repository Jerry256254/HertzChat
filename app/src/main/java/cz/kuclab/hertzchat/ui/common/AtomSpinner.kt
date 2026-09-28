package cz.kuclab.hertzchat.ui.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "atom" pending spinner (loading.dev style): three elliptical rings
 * tumbling inside a thin circle around a nucleus dot. Lives under outgoing
 * messages that are still waiting for delivery - never time-based, it spins
 * exactly while the message state says PENDING or SENT.
 */
@Composable
fun AtomSpinner(
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    /** One full tumble in ms - faster reads as "working", slower as "stuck". */
    durationMs: Int = 1400,
) {
    val transition = rememberInfiniteTransition(label = "atom")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing)),
        label = "atom-tumble",
    )
    Canvas(modifier = Modifier.size(size).then(modifier)) {
        val d = size.toPx().coerceAtMost(this.size.minDimension)
        val r = d / 2f
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val ring = Stroke(width = (d * 0.075f).coerceAtLeast(1.5f))
        // The cage the rings tumble inside.
        drawCircle(color = color.copy(alpha = 0.35f), radius = r, center = center, style = ring)
        // Three rings 120 degrees apart, tumbling together.
        for (i in 0..2) {
            rotate(angle + i * 120f, center) {
                val w = r * 2f
                val h = r * 2f * 0.42f
                drawOval(
                    color = color,
                    topLeft = Offset(center.x - w / 2f, center.y - h / 2f),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    style = ring,
                )
            }
        }
        drawCircle(color = color, radius = r * 0.16f, center = center)
    }
}
