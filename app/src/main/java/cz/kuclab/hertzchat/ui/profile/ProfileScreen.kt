package cz.kuclab.hertzchat.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.ui.chat.PhotoSource
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.GlassBar
import cz.kuclab.hertzchat.ui.common.HertzGlass
import cz.kuclab.hertzchat.ui.common.glassEdge
import cz.kuclab.hertzchat.ui.migration.generateQrBitmap

@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenQrExport: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val nickname by viewModel.nickname.collectAsState()
    val committedNickname by viewModel.committedNickname.collectAsState()
    val avatarVersion by viewModel.avatarVersion.collectAsState()
    val myQrText by viewModel.myHertzIdQrText.collectAsState()
    val i2pError by viewModel.i2pError.collectAsState()
    val canSave = nickname.trim().isNotEmpty() && nickname.trim() != committedNickname
    val clipboard = LocalClipboardManager.current

    var editingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> editingUri = uri }

    val hazeState = remember { HazeState() }
    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier.fillMaxSize().haze(hazeState, HertzGlass.hazeStyle()).verticalScroll(rememberScrollState()).padding(24.dp).padding(top = 68.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // The change badge is a *sibling* of the clipped photo, not a child:
                // inside the circle it got eaten by the clip and rendered cut off.
                Box(modifier = Modifier.size(112.dp).clickable { pickImage.launch("image/*") }) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        val avatarFile = remember(avatarVersion) { viewModel.avatarFile() }
                        if (avatarFile != null) {
                            AsyncImage(
                                model = avatarFile,
                                contentDescription = "Profilová fotka",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .glassEdge(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.CameraAlt,
                            contentDescription = "Změnit profilovou fotku",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            AppCard {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = viewModel::onNicknameChange,
                        label = { Text("Přezdívka") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = viewModel::saveNickname,
                        enabled = canSave,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Uložit")
                    }
                    Column {
                        Text("Moje ID", style = MaterialTheme.typography.labelSmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(viewModel.contactId, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { clipboard.setText(AnnotatedString(viewModel.contactId)) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = "Zkopírovat ID", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            AppCard {
                Column(
                    modifier = Modifier.padding(20.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Můj QR kód", fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        val qrText = myQrText
                        when {
                            qrText != null -> {
                                val bitmap = remember(qrText) { generateQrBitmap(qrText) }
                                Image(bitmap = bitmap.asImageBitmap(), contentDescription = "Můj QR kód")
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
                                        textAlign = TextAlign.Center,
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
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Ukaž tenhle QR kód příteli (nebo mu ID zkopíruj a pošli), ať tě může přidat. Bez toho tě nikdo nenajde - není tu žádný adresář uživatelů.",
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
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

            OutlinedButton(onClick = onOpenQrExport, modifier = Modifier.fillMaxWidth()) {
                Text("Přenést identitu na nové zařízení (QR)")
            }
        }
        GlassBar(title = "Profil", hazeState = hazeState, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
        }
    }

    editingUri?.let { uri ->
        AvatarCropDialog(
            source = PhotoSource.UriSource(uri),
            onCancel = { editingUri = null },
            onConfirm = { bytes ->
                viewModel.onAvatarPicked(bytes)
                editingUri = null
            },
        )
    }
}
