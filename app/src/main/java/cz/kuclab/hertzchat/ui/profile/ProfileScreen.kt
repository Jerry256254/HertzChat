package cz.kuclab.hertzchat.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.ui.chat.PhotoSource
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.GlassBar
import cz.kuclab.hertzchat.ui.common.glassEdge

@Composable
fun ProfileScreen(onBack: () -> Unit, onOpenQrExport: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val nickname by viewModel.nickname.collectAsState()
    val committedNickname by viewModel.committedNickname.collectAsState()
    val avatarVersion by viewModel.avatarVersion.collectAsState()
    val canSave = nickname.trim().isNotEmpty() && nickname.trim() != committedNickname
    val clipboard = LocalClipboardManager.current

    var editingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> editingUri = uri }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).padding(top = 68.dp),
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

            OutlinedButton(onClick = onOpenQrExport, modifier = Modifier.fillMaxWidth()) {
                Text("Přenést identitu na nové zařízení (QR)")
            }
        }
        GlassBar(title = "Profil", hazeState = null, onBack = onBack, modifier = Modifier.align(Alignment.TopCenter))
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
