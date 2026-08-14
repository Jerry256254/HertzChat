package cz.kuclab.hertzchat.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateLeft
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import cz.kuclab.hertzchat.media.ImageEditor

private data class AspectOption(val label: String, val ratio: Float?)

private val ASPECT_OPTIONS = listOf(
    AspectOption("Původní", null),
    AspectOption("1:1", 1f),
    AspectOption("4:3", 4f / 3f),
    AspectOption("16:9", 16f / 9f),
)

@Composable
fun ImageEditorDialog(uri: Uri, jpegQuality: Int = 95, onCancel: () -> Unit, onConfirm: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var selectedAspect by remember { mutableStateOf<Float?>(null) }

    val originalBitmap = remember(uri) {
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
    }

    if (originalBitmap == null) {
        onCancel()
        return
    }

    val editedBitmap = remember(rotationDegrees, selectedAspect) {
        val rotated = ImageEditor.rotate(originalBitmap, rotationDegrees)
        ImageEditor.cropToAspect(rotated, selectedAspect)
    }

    Dialog(onDismissRequest = onCancel) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
        ) {
            Image(
                bitmap = editedBitmap.asImageBitmap(),
                contentDescription = "Náhled úpravy obrázku",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ASPECT_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = selectedAspect == option.ratio,
                        onClick = { selectedAspect = option.ratio },
                        label = { Text(option.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                IconButton(onClick = { rotationDegrees -= 90f }) {
                    Icon(Icons.AutoMirrored.Filled.RotateLeft, contentDescription = "Otočit doleva", tint = Color.White)
                }
                IconButton(onClick = { rotationDegrees += 90f }) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = "Otočit doprava", tint = Color.White)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Zrušit") }
                Button(
                    onClick = { onConfirm(ImageEditor.toJpegBytes(editedBitmap, jpegQuality)) },
                    modifier = Modifier.weight(1f),
                ) { Text("Odeslat") }
            }
        }
    }
}
