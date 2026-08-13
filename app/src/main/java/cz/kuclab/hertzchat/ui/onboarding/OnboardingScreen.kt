package cz.kuclab.hertzchat.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Vítej v Hertz Chat", style = MaterialTheme.typography.titleLarge)
        Text(
            "Hertz Chat je čistě peer-to-peer šifrovaná chatovací aplikace. " +
                "Žádný server neukládá tvoje zprávy, kontakty ani média – všechno zůstává " +
                "pouze na tvém zařízení a je end-to-end šifrované.",
        )

        OutlinedTextField(
            value = state.nickname,
            onValueChange = viewModel::onNicknameChange,
            label = { Text("Přezdívka (nepovinné)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Necháš-li pole prázdné, vygeneruje se ti anonymní přezdívka. " +
                "Registrace přes telefonní číslo ani e-mail není potřeba – " +
                "tvoje identita je vázaná jen na toto zařízení.",
            style = MaterialTheme.typography.labelSmall,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = state.acceptedTerms, onCheckedChange = viewModel::onAcceptTermsChange)
            Text("Souhlasím s Podmínkami užití a Zásadami ochrany soukromí")
        }
        Row {
            TextButton(onClick = { showLegalText = TERMS_TEXT }) { Text("Zobrazit podmínky") }
            TextButton(onClick = { showLegalText = PRIVACY_TEXT }) { Text("Zobrazit soukromí") }
        }

        Button(
            onClick = { viewModel.createIdentity(onFinished) },
            enabled = state.acceptedTerms && !state.creating,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Vytvořit identitu a začít")
        }

        TextButton(onClick = onRestoreFromQr, modifier = Modifier.fillMaxWidth()) {
            Text("Už mám identitu na jiném zařízení - naskenovat QR", textAlign = TextAlign.Center)
        }
    }

    showLegalText?.let { text ->
        AlertDialog(
            onDismissRequest = { showLegalText = null },
            confirmButton = { TextButton(onClick = { showLegalText = null }) { Text("Zavřít") } },
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
