package cz.kuclab.hertzchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's three signature icons, drawn from open-source sets instead of
 * Material's defaults: a solid mic, an outlined files stack for attachments,
 * and an outlined gear for settings. Paths are byte-copies of the upstream
 * SVGs (24dp viewport), so they render exactly like the references.
 */
object HertzIcons {

    /** MynaUI `microphone-solid` (fill) - the voice record button. */
    val Mic: ImageVector by lazy {
        ImageVector.Builder("HertzMic", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M12 2.25c-2.51 0-4.75 1.767-4.75 4.179v5.142c0 2.412 2.24 4.179 4.75 4.179s4.75-1.767 4.75-4.179V6.43c0-2.412-2.24-4.179-4.75-4.179",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
            addPath(
                pathData = PathParser().parsePathString(
                    "M5.75 11a.75.75 0 0 0-1.5 0a7.75 7.75 0 0 0 7 7.714v1.536H8a.75.75 0 0 0 0 1.5h8a.75.75 0 0 0 0-1.5h-3.25v-1.536a7.75 7.75 0 0 0 7-7.714a.75.75 0 0 0-1.5 0a6.25 6.25 0 1 1-12.5 0",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /** Tabler `files` (2dp outline) - the attachment button. */
    val Attach: ImageVector by lazy {
        ImageVector.Builder("HertzAttach", 24.dp, 24.dp, 24f, 24f).apply {
            listOf(
                "M15 3v4a1 1 0 0 0 1 1h4",
                "M18 17h-7a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2h4l5 5v7a2 2 0 0 1 -2 2",
                "M16 17v2a2 2 0 0 1 -2 2h-7a2 2 0 0 1 -2 -2v-10a2 2 0 0 1 2 -2h2",
            ).forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }

    /** Lucide `settings` (2dp outline) - the settings button. */
    val Settings: ImageVector by lazy {
        ImageVector.Builder("HertzSettings", 24.dp, 24.dp, 24f, 24f).apply {
            listOf(
                "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
                // The hub: Lucide's <circle cx="12" cy="12" r="3"/> as two arcs.
                "M9 12a3 3 0 1 1 6 0a3 3 0 1 1-6 0",
            ).forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
}
