package cz.kuclab.hertzchat.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.kuclab.hertzchat.media.ImageEditor
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.GlassSurface
import cz.kuclab.hertzchat.ui.theme.HertzShapes

/** Where the photo came from - gallery/attachment (Uri) or the in-app camera (bytes). */
sealed interface PhotoSource {
    data class UriSource(val uri: Uri) : PhotoSource
    data class BytesSource(val bytes: ByteArray) : PhotoSource {
        override fun equals(other: Any?): Boolean = other is BytesSource && bytes.contentEquals(other.bytes)
        override fun hashCode(): Int = bytes.contentHashCode()
    }
}

private fun android.graphics.Rect.toComposeRect(): Rect = Rect(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())

private fun Rect.toAndroidRect(): android.graphics.Rect = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

private data class AspectOption(val label: String, val ratio: Float?)

private val ASPECT_OPTIONS = listOf(
    AspectOption("Původní", null),
    AspectOption("Volný", -1f),
    AspectOption("1:1", 1f),
    AspectOption("4:3", 4f / 3f),
    AspectOption("16:9", 16f / 9f),
)

private enum class EditorTool { CROP, DRAW, CENSOR }

private enum class StrokeKind { DRAW, CENSOR }

/** One finger stroke in *bitmap* pixels, so it stays glued to the photo whatever the preview size. */
private data class EditorStroke(
    val points: List<Offset>,
    val color: Color,
    /** Brush diameter in bitmap pixels. */
    val widthPx: Float,
    val kind: StrokeKind,
)

private val DRAW_COLORS = listOf(
    Color.White, Color.Black, Color.Red, Color(0xFFFFC107), Color(0xFF4CAF50), Color(0xFF2196F3),
)

/**
 * The photo editor every outgoing picture passes through: an interactive crop
 * rectangle (drag to move, corners/edges to resize, optional aspect lock) + rotate,
 * freehand drawing, and a mosaic censor brush. Confirms staged JPEG bytes into the
 * message input tray - it never sends anything itself.
 */
@Composable
fun PhotoEditorDialog(source: PhotoSource, jpegQuality: Int = 95, onCancel: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var tool by remember { mutableStateOf(EditorTool.CROP) }
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var selectedAspect by remember { mutableStateOf<Float?>(null) }
    var strokes by remember { mutableStateOf<List<EditorStroke>>(emptyList()) }
    var drawColor by remember { mutableStateOf(Color.Red) }
    var brushWidth by remember { mutableFloatStateOf(24f) }
    /** Interactive crop rectangle in rotated-bitmap pixels; null means no crop. */
    var cropRectBmp by remember { mutableStateOf<Rect?>(null) }

    val originalBitmap = remember(source) {
        when (source) {
            is PhotoSource.UriSource -> context.contentResolver.openInputStream(source.uri)?.use { BitmapFactory.decodeStream(it) }
            is PhotoSource.BytesSource -> BitmapFactory.decodeByteArray(source.bytes, 0, source.bytes.size)
        }
    }

    if (originalBitmap == null) {
        onCancel()
        return
    }

    val rotatedBitmap = remember(originalBitmap, rotationDegrees) {
        ImageEditor.rotate(originalBitmap, rotationDegrees)
    }
    // Rotation swaps the dimensions the rectangle lives in, so any stored rect is
    // re-seeded from the aspect choice whenever the rotated bitmap changes.
    androidx.compose.runtime.LaunchedEffect(rotatedBitmap) {
        cropRectBmp = selectedAspect?.let { aspect ->
            ImageEditor.defaultCropRect(rotatedBitmap.width, rotatedBitmap.height, aspect).toComposeRect()
        }
    }

    // Crop/rotate define the base every stroke is drawn onto - changing them clears
    // strokes (see the tool handlers below), since stored bitmap coordinates would no
    // longer match the new base. While the crop rectangle is being dragged the base
    // stays the uncropped photo (re-cutting a bitmap every drag frame would churn
    // tens of megabytes); the cut applies when leaving crop mode or on confirm.
    val baseBitmap = remember(rotatedBitmap, cropRectBmp, tool) {
        val rect = cropRectBmp
        if (rect == null || tool == EditorTool.CROP) rotatedBitmap
        else ImageEditor.cropToRect(rotatedBitmap, rect.toAndroidRect())
    }

    /** The exact bytes confirm sends: cropped base plus committed strokes, always consistent - every base-changing action clears strokes. */
    fun commitBitmap(): Bitmap {
        val rect = cropRectBmp
        val cropped = if (rect == null) rotatedBitmap else ImageEditor.cropToRect(rotatedBitmap, rect.toAndroidRect())
        return bakeStrokes(cropped, strokes)
    }

    // The committed result, recomputed only when strokes change (not per touch move).
    val bakedBitmap = remember(baseBitmap, strokes) { bakeStrokes(baseBitmap, strokes) }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(12.dp),
        ) {
            // The app's floating glass bar, not a plain icon row - same chrome
            // as every viewer, over the black editing surface.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Zrušit",
                    onClick = onCancel,
                )
                GlassSurface(
                    shape = HertzShapes.Pill,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                ) {
                    Text(
                        "Upravit fotku",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center).padding(vertical = 11.dp, horizontal = 16.dp),
                    )
                }
                GlassCircleButton(
                    icon = Icons.Filled.Check,
                    contentDescription = "Přidat",
                    onClick = { onConfirm(ImageEditor.toJpegBytes(commitBitmap(), jpegQuality)) },
                    accent = true,
                )
            }

            var liveStroke by remember { mutableStateOf<EditorStroke?>(null) }
            PhotoCanvas(
                // Crop mode shows the pre-crop photo under the interactive rectangle;
                // draw/censor modes show the cropped base strokes land on.
                bitmap = (if (tool == EditorTool.CROP) rotatedBitmap else bakedBitmap).asImageBitmap(),
                liveStroke = liveStroke.takeIf { tool != EditorTool.CROP },
                drawEnabled = tool != EditorTool.CROP,
                onStrokeStart = { point ->
                    val kind = if (tool == EditorTool.CENSOR) StrokeKind.CENSOR else StrokeKind.DRAW
                    val color = if (tool == EditorTool.CENSOR) Color.Black else drawColor
                    liveStroke = EditorStroke(listOf(point), color, brushWidth, kind)
                },
                onStrokeMove = { point ->
                    liveStroke = liveStroke?.copy(points = liveStroke!!.points + point)
                },
                onStrokeEnd = {
                    liveStroke?.let { if (it.points.size > 1) strokes = strokes + it }
                    liveStroke = null
                },
                cropRect = cropRectBmp.takeIf { tool == EditorTool.CROP },
                cropAspect = selectedAspect,
                onCropRectChange = {
                    cropRectBmp = it
                    strokes = emptyList()
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            if (tool == EditorTool.CROP) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ASPECT_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = selectedAspect == option.ratio,
                            onClick = {
                                strokes = emptyList()
                                selectedAspect = option.ratio
                                cropRectBmp = option.ratio?.let {
                                    ImageEditor.defaultCropRect(rotatedBitmap.width, rotatedBitmap.height, it).toComposeRect()
                                }
                            },
                            label = { Text(option.label) },
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(onClick = { strokes = emptyList(); rotationDegrees -= 90f }) {
                        Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Otočit doleva", tint = Color.White)
                    }
                    IconButton(onClick = { strokes = emptyList(); rotationDegrees += 90f }) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Otočit doprava", tint = Color.White)
                    }
                }
            } else {
                if (tool == EditorTool.DRAW) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DRAW_COLORS.forEach { color ->
                            val selected = drawColor == color
                            Box(
                                modifier = Modifier
                                    .size(if (selected) 34.dp else 28.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .then(if (selected) Modifier.border(2.dp, Color.White, CircleShape) else Modifier)
                                    .clickable { drawColor = color },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (tool == EditorTool.CENSOR) "Velikost" else "Štětec", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = brushWidth,
                        onValueChange = { brushWidth = it },
                        valueRange = 8f..120f,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { strokes = strokes.dropLast(1) }, enabled = strokes.isNotEmpty()) {
                        Icon(Icons.Filled.Undo, contentDescription = "Zpět", tint = Color.White)
                    }
                }
            }

            // One frosted tool pill instead of loose icons - and no duplicate
            // bottom buttons, the top bar already confirms and cancels.
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                GlassSurface(shape = HertzShapes.Pill) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ToolButton(Icons.Filled.Crop, "Ořez", tool == EditorTool.CROP) { tool = EditorTool.CROP }
                        ToolButton(Icons.Filled.Brush, "Kreslení", tool == EditorTool.DRAW) { tool = EditorTool.DRAW }
                        ToolButton(Icons.Filled.GridOn, "Cenzura", tool == EditorTool.CENSOR) { tool = EditorTool.CENSOR }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.primary else Color.White)
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * The photo with a touch layer: drags become strokes in bitmap coordinates (ContentScale.Fit
 * letterboxing is accounted for), and the in-progress stroke previews live on top.
 * In crop mode the same layer hosts the interactive crop rectangle instead.
 */
@Composable
private fun PhotoCanvas(
    bitmap: androidx.compose.ui.graphics.ImageBitmap,
    liveStroke: EditorStroke?,
    drawEnabled: Boolean,
    onStrokeStart: (Offset) -> Unit,
    onStrokeMove: (Offset) -> Unit,
    onStrokeEnd: () -> Unit,
    cropRect: Rect?,
    cropAspect: Float?,
    onCropRectChange: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val boxWpx = with(density) { maxWidth.toPx() }
        val boxHpx = with(density) { maxHeight.toPx() }
        val bmpW = bitmap.width.toFloat()
        val bmpH = bitmap.height.toFloat()
        val scale = minOf(boxWpx / bmpW, boxHpx / bmpH)
        val dispW = bmpW * scale
        val dispH = bmpH * scale
        val offX = (boxWpx - dispW) / 2f
        val offY = (boxHpx - dispH) / 2f

        fun toBitmap(touch: Offset): Offset =
            Offset(((touch.x - offX) / scale).coerceIn(0f, bmpW), ((touch.y - offY) / scale).coerceIn(0f, bmpH))

        // Read through a ref: re-keying the gesture detector on every rectangle
        // change would restart (and kill) the drag in progress.
        val latestCropRect by androidx.compose.runtime.rememberUpdatedState(cropRect)
        val latestOnCropChange by androidx.compose.runtime.rememberUpdatedState(onCropRectChange)

        Image(
            bitmap = bitmap,
            contentDescription = "Náhled úpravy obrázku",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(drawEnabled, scale, offX, offY) {
                    if (!drawEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { onStrokeStart(toBitmap(it)) },
                        onDrag = { change, _ -> onStrokeMove(toBitmap(change.position)) },
                        onDragEnd = onStrokeEnd,
                        onDragCancel = onStrokeEnd,
                    )
                }
                .pointerInput(cropRect != null, scale, offX, offY, cropAspect, bmpW, bmpH) {
                    if (cropRect == null) return@pointerInput
                    var drag: CropDrag? = null
                    val slop = with(density) { 24.dp.toPx() }
                    detectDragGestures(
                        onDragStart = { start ->
                            drag = latestCropRect?.let { classifyCropGrab(start, it, scale, offX, offY, slop, (cropAspect ?: 0f) > 0f) }
                        },
                        onDrag = { _, amount ->
                            val rect = latestCropRect ?: return@detectDragGestures
                            val mode = drag ?: return@detectDragGestures
                            val next = applyCropDrag(rect, mode, amount / scale, cropAspect, bmpW, bmpH)
                            if (next != rect) latestOnCropChange(next)
                        },
                        onDragEnd = { drag = null },
                        onDragCancel = { drag = null },
                    )
                },
        ) {
            liveStroke?.let { stroke ->
                if (stroke.points.size > 1) {
                    val path = Path().apply {
                        val first = stroke.points.first()
                        moveTo(offX + first.x * scale, offY + first.y * scale)
                        stroke.points.drop(1).forEach { lineTo(offX + it.x * scale, offY + it.y * scale) }
                    }
                    // Censor previews as a dark translucent band; the mosaic itself bakes on release.
                    val previewColor = if (stroke.kind == StrokeKind.CENSOR) Color.Black.copy(alpha = 0.75f) else stroke.color
                    drawPath(path, previewColor, style = Stroke(width = stroke.widthPx * scale))
                } else if (stroke.points.size == 1) {
                    val p = stroke.points.first()
                    drawCircle(
                        color = if (stroke.kind == StrokeKind.CENSOR) Color.Black.copy(alpha = 0.75f) else stroke.color,
                        radius = stroke.widthPx * scale / 2f,
                        center = Offset(offX + p.x * scale, offY + p.y * scale),
                    )
                }
            }
            cropRect?.let { drawCropOverlay(it, scale, offX, offY) }
        }
    }
}

/** Minimum crop rectangle side in bitmap pixels - smaller than this is a slip, not a crop. */
private const val MIN_CROP_PX = 64f

private sealed interface CropDrag {
    data object Move : CropDrag
    /** Corner resize around the fixed opposite [anchor] (bitmap coordinates). */
    data class Corner(val anchor: Offset) : CropDrag
    /** Single-edge resize in free mode: 0 = left, 1 = top, 2 = right, 3 = bottom. */
    data class Edge(val edge: Int) : CropDrag
}

/** Decides what a crop-mode drag does from where the finger landed (display pixels). */
private fun classifyCropGrab(start: Offset, rectBmp: Rect, scale: Float, offX: Float, offY: Float, slop: Float, ratioLocked: Boolean): CropDrag? {
    val l = offX + rectBmp.left * scale
    val t = offY + rectBmp.top * scale
    val r = offX + rectBmp.right * scale
    val b = offY + rectBmp.bottom * scale
    val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
    val anchors = listOf(rectBmp.bottomRight, Offset(rectBmp.left, rectBmp.bottom), Offset(rectBmp.right, rectBmp.top), rectBmp.topLeft)

    fun nearestCorner(): Int = corners.indices.minBy { (corners[it] - start).getDistance() }

    if (corners.indexOfFirst { (it - start).getDistance() <= slop } != -1) {
        return CropDrag.Corner(anchors[corners.indexOfFirst { (it - start).getDistance() <= slop }])
    }
    val nearLeft = kotlin.math.abs(start.x - l) <= slop && start.y in t..b
    val nearRight = kotlin.math.abs(start.x - r) <= slop && start.y in t..b
    val nearTop = kotlin.math.abs(start.y - t) <= slop && start.x in l..r
    val nearBottom = kotlin.math.abs(start.y - b) <= slop && start.x in l..r
    if (nearLeft || nearRight || nearTop || nearBottom) {
        // A locked ratio can't hold on a single edge - treat it as the nearest
        // corner so the rectangle keeps its proportions while resizing.
        if (ratioLocked) return CropDrag.Corner(anchors[nearestCorner()])
        return CropDrag.Edge(when { nearLeft -> 0; nearTop -> 1; nearRight -> 2; else -> 3 })
    }
    if (start.x in l..r && start.y in t..b) return CropDrag.Move
    return null
}

/** Applies one drag step (bitmap pixels) to the crop rectangle. Pure geometry - no Compose. */
private fun applyCropDrag(rect: Rect, mode: CropDrag, delta: Offset, aspect: Float?, bmpW: Float, bmpH: Float): Rect {
    return when (mode) {
        CropDrag.Move -> {
            val dx = delta.x.coerceIn(-rect.left, bmpW - rect.right)
            val dy = delta.y.coerceIn(-rect.top, bmpH - rect.bottom)
            rect.translate(dx, dy)
        }
        is CropDrag.Edge -> {
            var (l, t, r, b) = listOf(rect.left, rect.top, rect.right, rect.bottom)
            when (mode.edge) {
                0 -> l = (l + delta.x).coerceIn(0f, r - MIN_CROP_PX)
                1 -> t = (t + delta.y).coerceIn(0f, b - MIN_CROP_PX)
                2 -> r = (r + delta.x).coerceIn(l + MIN_CROP_PX, bmpW)
                else -> b = (b + delta.y).coerceIn(t + MIN_CROP_PX, bmpH)
            }
            Rect(l, t, r, b)
        }
        is CropDrag.Corner -> {
            val anchor = mode.anchor
            val grabX = if (anchor.x <= rect.left + 0.5f) rect.right else rect.left
            val grabY = if (anchor.y <= rect.top + 0.5f) rect.bottom else rect.top
            var w = kotlin.math.abs(grabX + delta.x - anchor.x)
            var h = kotlin.math.abs(grabY + delta.y - anchor.y)
            val ratio = aspect ?: 0f
            if (ratio > 0f) {
                if (w / h.coerceAtLeast(1f) > ratio) w = h * ratio else h = w / ratio
            }
            w = w.coerceAtLeast(MIN_CROP_PX)
            h = h.coerceAtLeast(MIN_CROP_PX)
            val sx = if (grabX + delta.x >= anchor.x) 1f else -1f
            val sy = if (grabY + delta.y >= anchor.y) 1f else -1f
            var l = (if (sx > 0) anchor.x else anchor.x - w).coerceIn(0f, bmpW)
            var t = (if (sy > 0) anchor.y else anchor.y - h).coerceIn(0f, bmpH)
            var r = (if (sx > 0) anchor.x + w else anchor.x).coerceIn(0f, bmpW)
            var bb = (if (sy > 0) anchor.y + h else anchor.y).coerceIn(0f, bmpH)
            if (r - l < MIN_CROP_PX || bb - t < MIN_CROP_PX) return rect
            Rect(l, t, r, bb)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCropOverlay(rect: Rect, scale: Float, offX: Float, offY: Float) {
    val l = offX + rect.left * scale
    val t = offY + rect.top * scale
    val r = offX + rect.right * scale
    val b = offY + rect.bottom * scale
    val dim = Color.Black.copy(alpha = 0.55f)
    drawRect(dim, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width, t))
    drawRect(dim, topLeft = Offset(0f, b), size = androidx.compose.ui.geometry.Size(size.width, size.height - b))
    drawRect(dim, topLeft = Offset(0f, t), size = androidx.compose.ui.geometry.Size(l, b - t))
    drawRect(dim, topLeft = Offset(r, t), size = androidx.compose.ui.geometry.Size(size.width - r, b - t))
    drawRect(Color.White, topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(r - l, b - t), style = Stroke(width = 2.dp.toPx()))
    // Rule-of-thirds grid.
    val gridColor = Color.White.copy(alpha = 0.55f)
    val gridWidth = 1.dp.toPx()
    for (i in 1..2) {
        val gx = l + (r - l) * i / 3f
        drawLine(gridColor, Offset(gx, t), Offset(gx, b), strokeWidth = gridWidth)
        val gy = t + (b - t) * i / 3f
        drawLine(gridColor, Offset(l, gy), Offset(r, gy), strokeWidth = gridWidth)
    }
    // Corner handles: white dots with a dark outline, visible on any photo.
    val handleR = 6.dp.toPx()
    listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach { center ->
        drawCircle(Color.Black.copy(alpha = 0.45f), radius = handleR + 2.dp.toPx(), center = center)
        drawCircle(Color.White, radius = handleR, center = center)
    }
}

/** Bakes committed strokes into a new bitmap: draw strokes as paint, censor strokes as mosaic tiles. */
private fun bakeStrokes(base: Bitmap, strokes: List<EditorStroke>): Bitmap {
    if (strokes.isEmpty()) return base
    val out = base.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(out)
    strokes.filter { it.kind == StrokeKind.DRAW }.forEach { stroke ->
        val paint = Paint().apply {
            color = stroke.color.toArgb()
            style = Paint.Style.STROKE
            strokeWidth = stroke.widthPx
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            isAntiAlias = true
        }
        canvas.drawPath(stroke.toAndroidPath(), paint)
    }
    strokes.filter { it.kind == StrokeKind.CENSOR }.forEach { stampMosaic(base, canvas, it) }
    return out
}

private fun EditorStroke.toAndroidPath(): android.graphics.Path {
    val path = androidx.compose.ui.graphics.Path().apply {
        if (points.isNotEmpty()) {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
    }
    return path.asAndroidPath()
}

/**
 * Stamps mosaic tiles along the stroke: each tile is filled with the average color of
 * the *original* photo underneath, so the censored area reads as pixelated photo
 * rather than a flat bar - while staying genuinely unreadable.
 */
private fun stampMosaic(base: Bitmap, canvas: android.graphics.Canvas, stroke: EditorStroke) {
    val tile = (minOf(base.width, base.height) / 36).coerceAtLeast(8)
    val radius = (stroke.widthPx / 2f).coerceAtLeast(tile.toFloat())
    val paint = Paint().apply { style = Paint.Style.FILL }
    val stamped = mutableSetOf<Pair<Int, Int>>()

    fun stampAt(cx: Float, cy: Float) {
        val left = (cx - radius).toInt().coerceAtLeast(0)
        val top = (cy - radius).toInt().coerceAtLeast(0)
        val right = (cx + radius).toInt().coerceAtMost(base.width - 1)
        val bottom = (cy + radius).toInt().coerceAtMost(base.height - 1)
        var tx = (left / tile) * tile
        while (tx < right) {
            var ty = (top / tile) * tile
            while (ty < bottom) {
                if (stamped.add(tx to ty)) {
                    paint.color = averageTileColor(base, tx, ty, tile)
                    canvas.drawRect(tx.toFloat(), ty.toFloat(), (tx + tile).toFloat(), (ty + tile).toFloat(), paint)
                }
                ty += tile
            }
            tx += tile
        }
    }

    if (stroke.points.size == 1) {
        stampAt(stroke.points.first().x, stroke.points.first().y)
        return
    }
    // Walk segments densely so fast swipes don't leave gaps between stamps.
    stroke.points.zipWithNext().forEach { (a, b) ->
        val dist = kotlin.math.hypot(b.x - a.x, b.y - a.y)
        val steps = (dist / (tile / 2f)).toInt().coerceAtLeast(1)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            stampAt(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
        }
    }
}

private fun averageTileColor(base: Bitmap, tx: Int, ty: Int, tile: Int): Int {
    val right = (tx + tile).coerceAtMost(base.width)
    val bottom = (ty + tile).coerceAtMost(base.height)
    var r = 0L
    var g = 0L
    var b = 0L
    var n = 0L
    // Sample every 3rd pixel - plenty for an average, much cheaper than every pixel.
    var y = ty
    while (y < bottom) {
        var x = tx
        while (x < right) {
            val pixel = base.getPixel(x, y)
            r += android.graphics.Color.red(pixel)
            g += android.graphics.Color.green(pixel)
            b += android.graphics.Color.blue(pixel)
            n++
            x += 3
        }
        y += 3
    }
    if (n == 0L) n = 1
    return android.graphics.Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
}
