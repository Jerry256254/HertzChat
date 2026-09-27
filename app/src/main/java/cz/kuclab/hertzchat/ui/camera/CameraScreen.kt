package cz.kuclab.hertzchat.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import cz.kuclab.hertzchat.media.PendingCaptureStore
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.util.concurrent.Executors

/**
 * The in-app camera: full-screen preview, best-quality JPEG capture, flash and
 * front/back switching. The captured photo is handed to [PendingCaptureStore] and
 * this screen pops - the chat opens it in the photo editor, staged, never auto-sent.
 */
@Composable
fun CameraScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }

    var useFront by remember { mutableStateOf(false) }
    var flashMode by remember { mutableStateOf(ImageCapture.FLASH_MODE_AUTO) }
    var captureError by remember { mutableStateOf<String?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).setJpegQuality(95).build() }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { cameraExecutor.shutdown() } }

    val captureStore = remember {
        EntryPointAccessors.fromApplication<PendingCaptureEntryPoint>(context.applicationContext).pendingCaptureStore()
    }

    fun takePhoto() {
        captureError = null
        imageCapture.flashMode = flashMode
        val outputFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            options,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val bytes = runCatching { outputFile.readBytes() }.getOrNull()
                    outputFile.delete()
                    if (bytes != null) {
                        captureStore.publish(bytes)
                        (context as? android.app.Activity)?.runOnUiThread { onDone() } ?: onDone()
                    } else {
                        (context as? android.app.Activity)?.runOnUiThread { captureError = "Fotku se nepodařilo uložit" }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    (context as? android.app.Activity)?.runOnUiThread { captureError = "Focení selhalo: ${exception.message}" }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!hasPermission) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Pro focení přímo v appce potřebujeme přístup k fotoaparátu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                )
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Povolit fotoaparát")
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                },
                update = { previewView ->
                    val providerFuture = ProcessCameraProvider.getInstance(context)
                    providerFuture.addListener({
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                        runCatching {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                        }
                    }, ContextCompat.getMainExecutor(context))
                },
            )
            Row(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                IconButton(onClick = { onDone() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět", tint = Color.White)
                }
                IconButton(onClick = {
                    flashMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_AUTO -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_OFF
                        else -> ImageCapture.FLASH_MODE_AUTO
                    }
                }) {
                    Icon(
                        when (flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_OFF -> Icons.Filled.FlashOff
                            else -> Icons.Filled.FlashAuto
                        },
                        contentDescription = "Blesk",
                        tint = Color.White,
                    )
                }
                IconButton(onClick = { useFront = !useFront }) {
                    Icon(Icons.Filled.Cameraswitch, contentDescription = "Přepnout foťák", tint = Color.White)
                }
            }
            captureError?.let {
                Text(
                    it,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(cz.kuclab.hertzchat.ui.theme.HertzShapes.Small)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp)
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.25f))
                    .border(4.dp, Color.White, CircleShape)
                    .clickable { takePhoto() },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PendingCaptureEntryPoint {
    fun pendingCaptureStore(): PendingCaptureStore
}
