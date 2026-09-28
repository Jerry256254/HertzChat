package cz.kuclab.hertzchat.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The app's signature icons, drawn from open-source sets instead of
 * Material's defaults. Paths are byte-copies of the upstream SVGs (24dp
 * viewport), so they render exactly like the references.
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

    /** Remix `space-ship-2-line` (fill) - the send button, a little rocket for every message. */
    val Send: ImageVector by lazy {
        ImageVector.Builder("HertzSend", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M10.95 17.293a1 1 0 0 1 1.413 1.415l-2.839 2.838a1 1 0 0 1-1.414-1.414zm-1.778-3.88a1 1 0 0 1 1.414 1.416l-5.657 5.656a1 1 0 1 1-1.414-1.414zm8.25-10.7c1.219-.49 2.455-.188 3.254.61c.798.799 1.099 2.035.61 3.253l-4.254 10.597a1 1 0 0 1-1.889-.095a12 12 0 0 0-3.045-5.177A12 12 0 0 0 6.92 8.856a1 1 0 0 1-.095-1.888zm-12.13 8.924a1 1 0 0 1 1.415 1.414L3.868 15.89a1 1 0 0 1-1.414-1.415zm13.97-6.9c-.23-.229-.615-.361-1.094-.169l-8.26 3.317a13.9 13.9 0 0 1 3.604 2.602a13.9 13.9 0 0 1 2.602 3.604l3.317-8.26c.192-.478.06-.865-.17-1.094",
                ).toNodes(),
                fill = SolidColor(Color.Black),
            )
        }.build()
    }

    /**
     * IconPark `down` (2dp outline) - the scroll-to-bottom button. Upstream is
     * a 48dp canvas, halved here to the shared 24dp viewport.
     */
    val ScrollDown: ImageVector by lazy {
        ImageVector.Builder("HertzScrollDown", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString("M18 9L12 15L6 9").toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    /** Tabler `play` (2dp outline) - voice bubbles, previews, video overlay. */
    val Play: ImageVector by lazy {
        ImageVector.Builder("HertzPlay", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M5 5v14a2 2 0 0 0 2.75 1.84L20 13.74a2 2 0 0 0 0-3.5L7.75 3.14A2 2 0 0 0 5 4.89",
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    /**
     * Tabler `pause` (2dp outline) - the playing state everywhere [Play] appears.
     * Upstream draws two `<rect rx=2>`; rects spelled out as rounded paths here.
     */
    val Pause: ImageVector by lazy {
        ImageVector.Builder("HertzPause", 24.dp, 24.dp, 24f, 24f).apply {
            listOf(
                "M6 4h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
                "M16 4h2a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z",
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

    /** Tabler `download` (2dp outline) - every save-to-device button on media. */
    val Download: ImageVector by lazy {
        ImageVector.Builder("HertzDownload", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M4 17v2a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2M7 11l5 5l5-5m-5-7v12",
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    /** Tabler `phone` (2dp outline) - start a voice call. */
    val Call: ImageVector by lazy {
        ImageVector.Builder("HertzCall", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M5 4h4l2 5l-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2",
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    /** Tabler `phone-off` (2dp outline) - hang up / decline a call. */
    val CallEnd: ImageVector by lazy {
        ImageVector.Builder("HertzCallEnd", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M3 21L21 3M5.831 14.161A15.95 15.95 0 0 1 3 6a2 2 0 0 1 2-2h4l2 5l-2.5 1.5q.162.33.345.645m1.751 2.277A11 11 0 0 0 13.5 15.5L15 13l5 2v4a2 2 0 0 1-2 2a15.96 15.96 0 0 1-10.344-4.657",
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
    }

    /** Tabler `microphone-off` (2dp outline) - the muted state on a call. */
    val MicOff: ImageVector by lazy {
        ImageVector.Builder("HertzMicOff", 24.dp, 24.dp, 24f, 24f).apply {
            listOf(
                "m3 3l18 18",
                "M9 5a3 3 0 0 1 6 0v5a3 3 0 0 1-.13.874m-2 2A3 3 0 0 1 9 10.002v-1",
                "M5 10a7 7 0 0 0 10.846 5.85m2-2A6.97 6.97 0 0 0 18.998 10M8 21h8m-4-4v4",
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

    /** Tabler `volume` (2dp outline) - speaker on a call. */
    val Speaker: ImageVector by lazy {
        ImageVector.Builder("HertzSpeaker", 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = PathParser().parsePathString(
                    "M15 8a5 5 0 0 1 0 8m2.7-11a9 9 0 0 1 0 14M6 15H4a1 1 0 0 1-1-1v-4a1 1 0 0 1 1-1h2l3.5-4.5A.8.8 0 0 1 11 5v14a.8.8 0 0 1-1.5.5z",
                ).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
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
