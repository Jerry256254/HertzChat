package cz.kuclab.hertzchat.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.migration.QrCodeScannerAnalyzer
import cz.kuclab.hertzchat.ui.migration.generateQrBitmap
import java.util.concurrent.Executors
import kotlinx.coroutines.delay
import org.briarproject.onionwrapper.TorWrapper

@Composable
fun ContactsScreen(
    onOpenChat: (String) -> Unit,
    onOpenAssistant: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val requests by viewModel.incomingRequests.collectAsState()
    val torState by viewModel.torState.collectAsState()
    val bootstrapPercent by viewModel.bootstrapPercent.collectAsState()
    val torError by viewModel.torError.collectAsState()
    val addError by viewModel.addError.collectAsState()
    val addSuccess by viewModel.addSuccess.collectAsState()
    val myQrText by viewModel.myHertzIdQrText.collectAsState()
    val mistralEnabled by viewModel.mistralEnabled.collectAsState()
    val showMistralContact by viewModel.showMistralContact.collectAsState()
    val clipboard = LocalClipboardManager.current

    var pastedId by remember { mutableStateOf("") }
    var scannerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(addSuccess) {
        if (addSuccess) {
            delay(2500)
            viewModel.clearAddSuccess()
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Kontakty") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showMistralContact) {
                item {
                    MistralAssistantCard(
                        configured = mistralEnabled,
                        onClick = { if (mistralEnabled) onOpenAssistant() else onOpenSettings() },
                    )
                }
            }
            item { TorStatusRow(torState, bootstrapPercent, torError, onRetry = viewModel::retryTor) }

            item {
                AppCard {
                    Column(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Moje Hertz ID", fontWeight = FontWeight.SemiBold)
                        Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            val qrText = myQrText
                            when {
                                qrText != null -> {
                                    val bitmap = remember(qrText) { generateQrBitmap(qrText) }
                                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Moje Hertz ID QR kód")
                                }
                                torError != null -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.size(220.dp),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            "Připojení k síti Tor selhalo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        OutlinedButton(onClick = viewModel::retryTor, modifier = Modifier.padding(top = 12.dp)) {
                                            Text("Zkusit znovu")
                                        }
                                    }
                                }
                                else -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(220.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                                        Text(
                                            "Připravuje se tvoje adresa v síti Tor...",
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            "Ukaž tenhle QR kód příteli (nebo mu ID zkopíruj a pošli), ať tě může přidat. Bez toho tě nikdo nenajde - není tu žádný adresář uživatelů.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        TextButton(
                            onClick = { myQrText?.let { clipboard.setText(AnnotatedString(it)) } },
                            enabled = myQrText != null,
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Zkopírovat ID")
                        }
                    }
                }
            }

            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Přidat kontakt", fontWeight = FontWeight.SemiBold)
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))
                        OutlinedButton(onClick = { scannerOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Naskenovat QR kód přítele")
                        }
                        OutlinedTextField(
                            value = pastedId,
                            onValueChange = { pastedId = it },
                            label = { Text("nebo sem vlož jeho Hertz ID") },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        Button(
                            onClick = { viewModel.addByHertzId(pastedId); pastedId = "" },
                            enabled = pastedId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        ) { Text("Odeslat žádost o přátelství") }

                        if (addSuccess) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                                Text("  Žádost odeslána", color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        addError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                    }
                }
            }

            if (requests.isNotEmpty()) {
                item { Text("Žádosti o přátelství", fontWeight = FontWeight.SemiBold) }
                items(requests, key = { it.contactId }) { request ->
                    AppCard {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(request.nickname)
                            Row {
                                TextButton(onClick = { viewModel.respond(request, false) }) { Text("Odmítnout") }
                                Button(onClick = { viewModel.respond(request, true) }) { Text("Přijmout") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (scannerOpen) {
        QrScannerDialog(
            onDismiss = { scannerOpen = false },
            onScanned = { text -> scannerOpen = false; viewModel.addByHertzId(text) },
        )
    }
}

@Composable
private fun MistralAssistantCard(configured: Boolean, onClick: () -> Unit) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.mistral_avatar),
                contentDescription = "Mistral AI",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(androidx.compose.foundation.shape.CircleShape),
            )
            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Mistral AI", fontWeight = FontWeight.SemiBold)
                Text(
                    if (configured) "Asistent appky - klepnutím zahájíš konverzaci" else "Asistent appky - klepnutím nastavíš přístup",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

@Composable
private fun TorStatusRow(state: TorWrapper.TorState?, bootstrapPercent: Int, error: String?, onRetry: () -> Unit) {
    val label = when {
        error != null -> "Nepodařilo se připojit k síti Tor"
        state == TorWrapper.TorState.CONNECTED -> "Připojeno k síti Tor"
        state == TorWrapper.TorState.CONNECTING || state == TorWrapper.TorState.STARTING || state == TorWrapper.TorState.STARTED ->
            "Připojování k síti Tor... $bootstrapPercent %"
        state == TorWrapper.TorState.DISABLED -> "Síť vypnutá"
        else -> "Navazuje se spojení..."
    }
    val color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.weight(1f))
            if (error != null) {
                TextButton(onClick = onRetry) { Text("Zkusit znovu") }
            }
        }
        if (error != null) {
            Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } else if (state != TorWrapper.TorState.CONNECTED) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
private fun QrScannerDialog(onDismiss: () -> Unit, onScanned: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasPermission = granted }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Filled.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Pro naskenování QR kódu potřebujeme přístup k fotoaparátu",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
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
                        val previewView = PreviewView(ctx)
                        val executor = Executors.newSingleThreadExecutor()
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(executor, QrCodeScannerAnalyzer { text -> onScanned(text) })
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                )
                Text(
                    "Namiř na Hertz ID QR kód přítele",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Zavřít",
                    tint = if (hasPermission) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
