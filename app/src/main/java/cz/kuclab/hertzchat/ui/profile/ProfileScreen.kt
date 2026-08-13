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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import cz.kuclab.hertzchat.ui.chat.ImageEditorDialog

@Composable
fun ProfileScreen(onOpenQrExport: () -> Unit, viewModel: ProfileViewModel = hiltViewModel()) {
    val nickname by viewModel.nickname.collectAsState()
    val avatarVersion by viewModel.avatarVersion.collectAsState()

    var editingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> editingUri = uri }

    Scaffold(topBar = { TopAppBar(title = { Text("Profil") }) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { pickImage.launch("image/*") },
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
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
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

            Card {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nickname,
                        onValueChange = viewModel::onNicknameChange,
                        label = { Text("Přezdívka") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Column {
                        Text("Moje ID", style = MaterialTheme.typography.labelSmall)
                        Text(viewModel.contactId, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            OutlinedButton(onClick = onOpenQrExport, modifier = Modifier.fillMaxWidth()) {
                Text("Přenést identitu na nové zařízení (QR)")
            }
        }
    }

    editingUri?.let { uri ->
        ImageEditorDialog(
            uri = uri,
            onCancel = { editingUri = null },
            onConfirm = { bytes ->
                viewModel.onAvatarPicked(bytes)
                editingUri = null
            },
        )
    }
}
