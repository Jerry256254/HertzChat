package cz.kuclab.hertzchat.ui.profile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import cz.kuclab.hertzchat.media.ImageEditor
import cz.kuclab.hertzchat.ui.chat.PhotoSource
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.common.GlassSurface
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * The avatar picker: choose a photo, then frame it in the one fixed round crop -
 * pinch to zoom, drag to pan, nothing else. The confirmed square JPEG is what the
 * circle avatars clip everywhere, so the round overlay here is exactly the result.
 */
@Composable
fun AvatarCropDialog(source: PhotoSource, onCancel: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(source) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(source) {
        bitmap = withContext(Dispatchers.IO) { decodeAvatarBitmap(context, source) }
    }

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val bmp = bitmap
            if (bmp == null) {
                Text(
                    "Načítání…",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                AvatarCropArea(bitmap = bmp, onConfirm = onConfirm)
            }
            // Glass controls over the black surface - the app's chrome, not the
            // editor's plain icon row.
            Row(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp, bottom = 8.dp),
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
                        "Profilová fotka",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center).padding(vertical = 11.dp, horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AvatarCropArea(bitmap: Bitmap, onConfirm: (ByteArray) -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val cropPx = with(density) { (minOf(maxWidth, maxHeight) - 48.dp).toPx() }.coerceAtLeast(1f)

        fun clampOffsets() {
            // The photo may never uncover the crop square: panning stops where
            // its edge would cross the square's edge.
            val limit = cropPx * (zoom - 1f) / 2f
            offsetX = offsetX.coerceIn(-limit, limit)
            offsetY = offsetY.coerceIn(-limit, limit)
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(with(density) { cropPx.toDp() })
                    .pointerInput(cropPx) {
                        detectTransformGestures { _, pan, gestureZoom, _ ->
                            zoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                            clampOffsets()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Laid out at exactly the crop size with Crop, so gesture zoom 1
                // is the full photo and every zoom level stays a centred cover.
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = zoom,
                            scaleY = zoom,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                )
                // Dim everything outside the round crop - the hole is the photo.
                // Offscreen: without it the Clear circle would punch through the
                // photo down to the app behind the dialog instead of just the dim.
                Canvas(
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                    },
                ) {
                    drawRect(Color.Black.copy(alpha = 0.55f))
                    drawCircle(
                        color = Color.Transparent,
                        radius = cropPx / 2f,
                        blendMode = androidx.compose.ui.graphics.BlendMode.Clear,
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = cropPx / 2f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f * density.density),
                    )
                }
            }
            Text(
                "Přibliž a posuň fotku do kruhu",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 20.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 16.dp),
            ) {
                GlassCircleButton(
                    icon = Icons.Filled.Check,
                    contentDescription = "Použít fotku",
                    onClick = {
                        val rect = cropRect(bitmap, cropPx, zoom, offsetX, offsetY)
                        onConfirm(ImageEditor.toJpegBytes(ImageEditor.cropToRect(bitmap, rect)))
                    },
                    size = 56.dp,
                    accent = true,
                )
            }
        }
    }
}

/**
 * Maps the visible crop square back into bitmap pixels: the photo is drawn
 * covering the square then zoomed by [zoom] around its centre and shifted by
 * ([offsetX], [offsetY]), so the square's corners invert through that transform.
 */
private fun cropRect(bitmap: Bitmap, cropPx: Float, zoom: Float, offsetX: Float, offsetY: Float): android.graphics.Rect {
    val drawn = max(cropPx / bitmap.width, cropPx / bitmap.height)
    val scale = drawn * zoom
    // The drawn bitmap is centred in the square before the gesture shift.
    val left = (cropPx - bitmap.width * drawn) / 2f
    val top = (cropPx - bitmap.height * drawn) / 2f
    val invLeft = (0f - left - offsetX) / scale
    val invTop = (0f - top - offsetY) / scale
    val side = cropPx / scale
    return android.graphics.Rect(
        invLeft.toInt().coerceIn(0, bitmap.width - 1),
        invTop.toInt().coerceIn(0, bitmap.height - 1),
        (invLeft + side).toInt().coerceIn(1, bitmap.width),
        (invTop + side).toInt().coerceIn(1, bitmap.height),
    )
}

private fun decodeAvatarBitmap(context: android.content.Context, source: PhotoSource): Bitmap? {
    val bytes = when (source) {
        is PhotoSource.UriSource -> runCatching {
            context.contentResolver.openInputStream(source.uri)?.use { it.readBytes() }
        }.getOrNull() ?: return null
        is PhotoSource.BytesSource -> source.bytes
    }
    if (bytes.isEmpty()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    // The crop math runs on this decode - cap it so a 48MP photo doesn't eat RAM.
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1024) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
}
