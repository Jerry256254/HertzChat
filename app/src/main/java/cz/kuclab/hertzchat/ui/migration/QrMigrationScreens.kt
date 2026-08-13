package cz.kuclab.hertzchat.ui.migration

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import java.util.concurrent.Executors

@Composable
fun QrExportScreen(onDone: () -> Unit, viewModel: QrMigrationViewModel = hiltViewModel()) {
    val bitmap = remember { generateQrBitmap(viewModel.exportPayload()) }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Naskenuj tímto QR kódem nové zařízení, abys na něm pokračoval se stejnou identitou.")
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = "QR kód identity")
            Text(
                "Přenáší se jen tvůj kryptografický klíč identity, ne historie zpráv či média - " +
                    "ty zůstávají pouze na tomto zařízení, protože nejsou nikde v cloudu.",
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

    Scaffold { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Namiř na QR kód zobrazený na starém zařízení v Profil → Přenést identitu.",
                modifier = Modifier.padding(16.dp),
            )
            if (hasPermission && !imported) {
                AndroidView(
                    modifier = Modifier.fillMaxWidth().weight(1f),
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
            }
            error?.let { Text(it, modifier = Modifier.padding(16.dp)) }
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
