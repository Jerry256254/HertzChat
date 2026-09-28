package cz.kuclab.hertzchat.ui.file

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.ui.common.GlassBar
import cz.kuclab.hertzchat.ui.common.GlassCircleButton
import cz.kuclab.hertzchat.ui.theme.HertzIcons
import cz.kuclab.hertzchat.ui.common.HertzGlass
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Renders text past this size truncated - a 50MB log must not become a 50MB Text composable. */
private const val MAX_TEXT_PREVIEW_BYTES = 512 * 1024

private fun isPreviewableText(mimeType: String?, name: String): Boolean {
    if (mimeType?.startsWith("text/") == true) return true
    if (mimeType == "application/json") return true
    val lower = name.lowercase()
    return listOf(".txt", ".md", ".markdown", ".json", ".csv", ".log", ".xml", ".yml", ".yaml", ".ini", ".cfg", ".conf", ".srt", ".vtt").any { lower.endsWith(it) }
}

private fun isPdf(mimeType: String?, name: String): Boolean =
    mimeType == "application/pdf" || name.lowercase().endsWith(".pdf")

/**
 * The built-in, offline file viewer: text-like files render as monospace, PDFs page by
 * page via the platform renderer, everything else offers download or "open in another
 * app". Nothing ever leaves the device to preview - the file stays in private storage.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewerScreen(onBack: () -> Unit, viewModel: FileViewerViewModel = hiltViewModel()) {
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.notice.collect { snackbar.showSnackbar(it) }
    }

    val file = message?.mediaPath?.let { File(it) }?.takeIf { it.exists() }
    val name = message?.mediaFileName ?: file?.name ?: "Soubor"
    val mime = message?.mediaMimeType

    val hazeState = remember { HazeState() }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Box(modifier = Modifier.fillMaxSize().haze(hazeState, HertzGlass.hazeStyle())) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 76.dp)) {
            when {
                message == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                file == null -> UnsupportedContent(
                    name = name,
                    reason = "Soubor už není na zařízení k dispozici.",
                    onOpenExternal = null,
                    onDownload = null,
                )
                isPreviewableText(mime, name) -> TextPreview(file = file)
                isPdf(mime, name) -> PdfPreview(file = file)
                else -> UnsupportedContent(
                    name = name,
                    reason = "Tenhle typ souboru vestavěný prohlížeč neumí - můžeš si ho stáhnout nebo otevřít v jiné appce.",
                    onOpenExternal = {
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                setDataAndType(uri, mime ?: "*/*")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(intent, name))
                        }
                    },
                    onDownload = viewModel::download,
                )
            }
            }
            }
            GlassBar(
                title = name,
                hazeState = hazeState,
                onBack = onBack,
                actions = {
                    if (file != null) {
                        GlassCircleButton(
                            icon = HertzIcons.Download,
                            contentDescription = "Stáhnout",
                            onClick = viewModel::download,
                            hazeState = hazeState,
                        )
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun TextPreview(file: File) {
    var text by remember(file) { mutableStateOf<String?>(null) }
    var truncated by remember(file) { mutableStateOf(false) }
    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            // Capped read: opening a huge log must not load the whole file into RAM.
            val buf = ByteArray(MAX_TEXT_PREVIEW_BYTES + 1)
            var read = 0
            runCatching {
                file.inputStream().use { input ->
                    while (read < buf.size) {
                        val n = input.read(buf, read, buf.size - read)
                        if (n < 0) break
                        read += n
                    }
                }
            }
            truncated = read > MAX_TEXT_PREVIEW_BYTES
            text = buf.copyOf(minOf(read, MAX_TEXT_PREVIEW_BYTES)).toString(Charsets.UTF_8)
        }
    }
    val content = text
    if (content == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        // Pinch scales the text; panning is deliberately left alone so the
        // vertical scroll underneath keeps working.
        var textScale by remember(file) { mutableFloatStateOf(1f) }
        Column(modifier = Modifier.fillMaxSize()) {
            if (truncated) {
                Text(
                    "Náhled je zkrácený (prvních 512 kB)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            Text(
                content,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .graphicsLayer(scaleX = textScale, scaleY = textScale)
                    .pointerInput(file) {
                        detectTransformGestures { _, _, zoom, _ ->
                            textScale = (textScale * zoom).coerceIn(1f, 4f)
                        }
                    }
                    .padding(16.dp),
            )
        }
    }
}

@Composable
private fun PdfPreview(file: File) {
    var pageCount by remember(file) { mutableIntStateOf(0) }
    var pageIndex by remember(file) { mutableIntStateOf(0) }
    var bitmap by remember(file) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }

    val renderer = remember(file) {
        runCatching { PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) }.getOrNull()
    }
    DisposableEffect(file) {
        onDispose { runCatching { renderer?.close() } }
    }

    LaunchedEffect(renderer, pageIndex) {
        val r = renderer
        if (r == null) {
            failed = true
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching {
                pageCount = r.pageCount
                r.openPage(pageIndex.coerceIn(0, r.pageCount - 1)).use { page ->
                    // 3x rasterization so pinch-zoom stays crisp, capped so a huge
                    // page doesn't OOM the viewer.
                    val scale = 3f
                    val width = (page.width * scale).toInt().coerceAtMost(3200)
                    val height = (page.height * width / page.width).coerceAtMost(3200)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }.onSuccess { bitmap = it }.onFailure { failed = true }
        }
    }

    var pdfScale by remember(file, pageIndex) { mutableFloatStateOf(1f) }
    var pdfOffset by remember(file, pageIndex) { mutableStateOf(Offset.Zero) }
    fun resetPdfZoom() {
        pdfScale = 1f
        pdfOffset = Offset.Zero
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                failed -> Text("PDF se nepodařilo otevřít", color = MaterialTheme.colorScheme.onSurfaceVariant)
                bitmap == null -> CircularProgressIndicator()
                else -> Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Strana ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer(
                            scaleX = pdfScale,
                            scaleY = pdfScale,
                            translationX = pdfOffset.x,
                            translationY = pdfOffset.y,
                        )
                        .pointerInput(file, pageIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (pdfScale > 1f) resetPdfZoom() else pdfScale = 2.5f
                                },
                            )
                        }
                        .pointerInput(file, pageIndex) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val next = (pdfScale * zoom).coerceIn(1f, 4f)
                                pdfScale = next
                                pdfOffset = if (next == 1f) {
                                    Offset.Zero
                                } else {
                                    // Bounded pan: the page can't be pushed clean off screen.
                                    val bound = 1200f * next
                                    Offset(
                                        (pdfOffset.x + pan.x).coerceIn(-bound, bound),
                                        (pdfOffset.y + pan.y).coerceIn(-bound, bound),
                                    )
                                }
                            }
                        },
                )
            }
        }
        if (pageCount > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { pageIndex = (pageIndex - 1 + pageCount) % pageCount }, enabled = bitmap != null) {
                    Icon(Icons.Filled.ChevronLeft, contentDescription = "Předchozí strana")
                }
                Text("${pageIndex + 1} / $pageCount", style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = { pageIndex = (pageIndex + 1) % pageCount }, enabled = bitmap != null) {
                    Icon(Icons.Filled.ChevronRight, contentDescription = "Další strana")
                }
            }
        }
    }
}

@Composable
private fun UnsupportedContent(name: String, reason: String, onOpenExternal: (() -> Unit)?, onDownload: (() -> Unit)?) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
        Text(
            reason,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
        )
        if (onDownload != null) {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(HertzIcons.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Stáhnout")
            }
        }
        if (onOpenExternal != null) {
            OutlinedButton(onClick = onOpenExternal, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Otevřít v jiné appce")
            }
        }
    }
}
