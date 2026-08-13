package cz.kuclab.hertzchat.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import cz.kuclab.hertzchat.R
import cz.kuclab.hertzchat.ui.common.AppCard
import cz.kuclab.hertzchat.ui.common.LanguagePickerRow

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    onRestoreFromQr: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showLegalText by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.onboarding_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AppCard {
            Column(modifier = Modifier.padding(16.dp)) {
                LanguagePickerRow(
                    label = stringResource(R.string.onboarding_language_label),
                    currentCode = state.languageCode,
                    onChange = viewModel::onLanguageChange,
                )
            }
        }

        AppCard {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = viewModel::onNicknameChange,
                    label = { Text(stringResource(R.string.onboarding_nickname_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text(
                    stringResource(R.string.onboarding_nickname_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.acceptedTerms, onCheckedChange = viewModel::onAcceptTermsChange)
                Text(stringResource(R.string.onboarding_accept_terms), style = MaterialTheme.typography.bodyMedium)
            }
            Row {
                TextButton(onClick = { showLegalText = TERMS_TEXT }) { Text(stringResource(R.string.onboarding_show_terms)) }
                TextButton(onClick = { showLegalText = PRIVACY_TEXT }) { Text(stringResource(R.string.onboarding_show_privacy)) }
            }
        }

        Button(
            onClick = { viewModel.createIdentity(onFinished) },
            enabled = state.acceptedTerms && !state.creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.onboarding_create_identity))
        }

        TextButton(onClick = onRestoreFromQr, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.onboarding_restore_qr), textAlign = TextAlign.Center)
        }
    }

    showLegalText?.let { text ->
        AlertDialog(
            onDismissRequest = { showLegalText = null },
            confirmButton = { TextButton(onClick = { showLegalText = null }) { Text(stringResource(R.string.common_close)) } },
            text = {
                Text(
                    text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}
