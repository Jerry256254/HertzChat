package cz.kuclab.hertzchat.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.GlassBar
import cz.kuclab.hertzchat.ui.common.GlassDialogTheme
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.WindowBlurBehind
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import cz.kuclab.hertzchat.ui.migration.QrCodeScannerAnalyzer
import cz.kuclab.hertzchat.ui.theme.HertzShapes
import cz.kuclab.hertzchat.ui.migration.generateQrBitmap
import java.util.concurrent.Executors
import kotlinx.coroutines.delay

@Composable
fun ContactsScreen(
    onOpenChat: (String) -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val i2pError by viewModel.i2pError.collectAsState()
    val addError by viewModel.addError.collectAsState()
    val addSuccess by viewModel.addSuccess.collectAsState()
    val myQrText by viewModel.myHertzIdQrText.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val blocked by viewModel.blockedContacts.collectAsState()
    val clipboard = LocalClipboardManager.current

    var pastedId by remember { mutableStateOf("") }
    var scannerOpen by remember { mutableStateOf(false) }
    var createGroupOpen by remember { mutableStateOf(false) }
    var contactSearch by remember { mutableStateOf("") }
    val visibleContacts = remember(contacts, contactSearch) {
        if (contactSearch.isBlank()) contacts
        else contacts.filter { it.nickname.contains(contactSearch, ignoreCase = true) }
    }

    LaunchedEffect(addSuccess) {
        if (addSuccess) {
            delay(2500)
            viewModel.clearAddSuccess()
        }
    }

    val hazeState = remember { HazeState() }
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().haze(hazeState, HertzGlass.hazeStyle()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 92.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                                i2pError != null -> {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.size(220.dp),
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            "Připojení k síti I2P selhalo",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        )
                                        OutlinedButton(onClick = viewModel::retryI2p, modifier = Modifier.padding(top = 12.dp)) {
                                            Text("Zkusit znovu")
                                        }
                                    }
                                }
                                else -> {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(220.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.padding(bottom = 12.dp))
                                        Text(
                                            "Připravuje se tvoje adresa v síti I2P...",
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

            item {
                AppCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Moje kontakty", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = contactSearch,
                            onValueChange = { contactSearch = it },
                            label = { Text("Hledat kontakt") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        if (visibleContacts.isEmpty()) {
                            Text(
                                if (contacts.isEmpty()) "Zatím tu nikoho nemáš - přidej si přítele výše." else "Nikdo takový tu není.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                        } else {
                            visibleContacts.forEach { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenChat(contact.contactId) }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (contact.avatarPath != null) {
                                            AsyncImage(
                                                model = java.io.File(contact.avatarPath),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(44.dp).clip(CircleShape),
                                            )
                                        } else {
                                            Text(
                                                contact.nickname.take(1).uppercase(),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Text(
                                        contact.nickname,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(start = 12.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { createGroupOpen = true },
                    enabled = contacts.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Groups, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Vytvořit skupinu")
                }
                if (contacts.isEmpty()) {
                    Text(
                        "Skupinu vytvoříš, jakmile si přidáš aspoň jeden kontakt.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            if (blocked.isNotEmpty()) {
                item {
                    AppCard {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Blokované kontakty", fontWeight = FontWeight.SemiBold)
                            blocked.forEachIndexed { index, contact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(contact.nickname)
                                    TextButton(onClick = { viewModel.unblock(contact.contactId) }) { Text("Odblokovat") }
                                }
                                if (index != blocked.lastIndex) HorizontalDivider()
                            }
                        }
                    }
                }
            }

        }
        GlassBar(title = "Kontakty", hazeState = hazeState, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    if (scannerOpen) {
        QrScannerDialog(
            onDismiss = { scannerOpen = false },
            onScanned = { text -> scannerOpen = false; viewModel.addByHertzId(text) },
        )
    }

    if (createGroupOpen) {
        CreateGroupDialog(
            contacts = contacts,
            onDismiss = { createGroupOpen = false },
            onCreate = { name, ids -> viewModel.createGroup(name, ids); createGroupOpen = false },
        )
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
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color.Black)
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Zavřít", tint = androidx.compose.ui.graphics.Color.White)
                }
                Text(
                    "Naskenovat QR kód",
                    color = androidx.compose.ui.graphics.Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
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
                        color = androidx.compose.ui.graphics.Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
                    )
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Povolit fotoaparát")
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(20.dp)
                        .clip(HertzShapes.Dialog),
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val executor = Executors.newSingleThreadExecutor()
                            // CameraX analyses at 640x480 unless told otherwise, and that is
                            // not enough for this code: a Hertz ID is a ~670-character payload,
                            // so the QR runs to well over a hundred modules per side. Held at a
                            // comfortable distance it covers maybe half the frame, leaving
                            // roughly two pixels per module - under what a decoder can resolve.
                            // At 1280x720 the same code lands at four to five pixels per module.
                            val analyzer = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setResolutionSelector(
                                    ResolutionSelector.Builder()
                                        .setResolutionStrategy(
                                            ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                                        )
                                        .build(),
                                )
                                .build()
                            analyzer.setAnalyzer(executor, QrCodeScannerAnalyzer { text -> onScanned(text) })
                            val providerFuture = ProcessCameraProvider.getInstance(ctx)
                            providerFuture.addListener({
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                                provider.unbindAll()
                                camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                    )
                    ViewfinderOverlay(modifier = Modifier.fillMaxSize())
                }
                Text(
                    "Namiř na Hertz ID QR kód přítele",
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    FilledTonalButton(
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    ) {
                        Icon(
                            if (torchOn) Icons.Filled.FlashlightOff else Icons.Filled.FlashlightOn,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(if (torchOn) "  Zhasnout světlo" else "  Rozsvítit světlo")
                    }
                } else {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(bottom = 16.dp))
                }
            }
        }
    }
}

/** Rounded-corner brackets marking the scan area over the camera preview. */
@Composable
private fun ViewfinderOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = size.minDimension * 0.68f
        val left = (size.width - side) / 2f
        val top = (size.height - side) / 2f
        val arm = side * 0.14f
        val stroke = 5.dp.toPx()
        val color = androidx.compose.ui.graphics.Color.White
        val round = 8.dp.toPx()
        // Dim everything outside the frame so the eye goes to the code.
        drawRect(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = androidx.compose.ui.geometry.Size(size.width, top),
        )
        drawRect(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, top + side),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - top - side),
        )
        drawRect(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(left, side),
        )
        drawRect(
            androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f),
            topLeft = androidx.compose.ui.geometry.Offset(left + side, top),
            size = androidx.compose.ui.geometry.Size(size.width - left - side, side),
        )
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(color, androidx.compose.ui.geometry.Offset(x, y + dy * round), androidx.compose.ui.geometry.Offset(x, y + dy * arm), strokeWidth = stroke, cap = StrokeCap.Round)
            drawLine(color, androidx.compose.ui.geometry.Offset(x + dx * round, y), androidx.compose.ui.geometry.Offset(x + dx * arm, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
        corner(left, top, 1f, 1f)
        corner(left + side, top, -1f, 1f)
        corner(left, top + side, 1f, -1f)
        corner(left + side, top + side, -1f, -1f)
    }
}

@Composable
private fun CreateGroupDialog(
    contacts: List<cz.kuclab.hertzchat.data.db.ContactEntity>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateOf(setOf<String>()) }

    GlassDialogTheme {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { WindowBlurBehind(); Text("Vytvořit skupinu") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Název skupiny") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    "Vyber kontakty (skupina funguje jen mezi vzájemnými kontakty)",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                contacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected.value = if (contact.contactId in selected.value) {
                                    selected.value - contact.contactId
                                } else {
                                    selected.value + contact.contactId
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = contact.contactId in selected.value, onCheckedChange = null)
                        Text(contact.nickname, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name, selected.value.toList()) },
                enabled = name.isNotBlank() && selected.value.isNotEmpty(),
            ) { Text("Vytvořit") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Zrušit") } },
    )
    }
}
