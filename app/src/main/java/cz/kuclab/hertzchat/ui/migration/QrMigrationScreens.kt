package cz.kuclab.hertzchat.ui.migration

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.camera.view.PreviewView
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.ui.common.AppCard
import java.util.concurrent.Executors

@Composable
fun QrExportScreen(onDone: () -> Unit, viewModel: QrMigrationViewModel = hiltViewModel()) {
    val bitmap = remember { generateQrBitmap(viewModel.exportPayload()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Přenos identity") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                "Naskenuj tímto QR kódem nové zařízení, abys na něm pokračoval se stejnou identitou.",
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            AppCard {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "QR kód identity",
                    modifier = Modifier.padding(20.dp),
                )
            }
            Text(
                "Přenáší se jen tvůj kryptografický klíč identity, ne historie zpráv či média - " +
                    "ty zůstávají pouze na tomto zařízení, protože nejsou nikde v cloudu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Hotovo") }
        }
    }
}

@Composable
fun QrImportScreen(onDone: () -> Unit, viewModel: QrMigrationViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    var imported by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Naskenovat identitu") },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Namiř na QR kód zobrazený na starém zařízení v Profil → Přenést identitu.",
                modifier = Modifier.padding(16.dp),
            )
            if (hasPermission && !imported) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp)),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val executor = Executors.newSingleThreadExecutor()
                        val analyzer = androidx.camera.core.ImageAnalysis.Builder()
                            .setBackpressureStrategy(androidx.camera.core.ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analyzer.setAnalyzer(
                            executor,
                            QrCodeScannerAnalyzer { text ->
                                if (!imported) {
                                    val ok = viewModel.importPayload(text)
                                    if (ok) {
                                        imported = true
                                    } else {
                                        error = "Neplatný nebo poškozený QR kód"
                                    }
                                }
                            },
                        )
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
            } else if (!hasPermission) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Pro naskenování QR kódu potřebujeme přístup k fotoaparátu.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 16.dp),
                    ) { Text("Povolit fotoaparát") }
                }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (imported) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Identita byla importována. Aplikace se teď restartuje, aby se změna projevila.")
                    Button(onClick = { viewModel.restartApp(context); onDone() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Restartovat")
                    }
                }
            }
        }
    }
}
