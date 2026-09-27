package cz.kuclab.hertzchat.ui.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

/** The KucLab Hertz web assistant, embedded as a WebView so it never leaves the app. Trailing slash on purpose - the server 301s the bare path here. */
const val HERTZ_ASSISTANT_URL = "https://kuclab.org/hertz/"

/** Stable synthetic id for the assistant's row in the chat list - it isn't a real contact. */
const val HERTZ_ASSISTANT_CONTACT_ID = "hertz-ai-assistant"

/**
 * The Hertz AI assistant (kuclab.org/hertz) running inside the app. The user signs in
 * with their KucLab account right here and the session cookie persists, so the login
 * survives restarts just like in a browser. Navigation within kuclab.org stays in the
 * WebView; links anywhere else open in the system browser.
 */
@Composable
fun HertzAssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            // The assistant is a stateful web app (login session, credits, history) -
            // without persistent cookies the user would have to sign in on every visit.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val url = request.url
                    return if (url.host?.endsWith("kuclab.org") == true) {
                        false
                    } else {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url)) }
                        true
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    loading = false
                    CookieManager.getInstance().flush()
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        loading = false
                        loadError = true
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    // Voice input ("Poslouchám…") asks for the microphone - grant only what
                    // this app itself already holds, never anything more.
                    val granted = request.resources.filter {
                        it != PermissionRequest.RESOURCE_AUDIO_CAPTURE ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    }.toTypedArray()
                    if (granted.isNotEmpty()) request.grant(granted) else request.deny()
                }
            }
            loadUrl(HERTZ_ASSISTANT_URL)
        }
    }
    DisposableEffect(Unit) {
        onDispose { webView.destroy() }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Hertz AI") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zpět") } },
                actions = {
                    IconButton(onClick = { loadError = false; loading = true; webView.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Obnovit")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
            if (loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
            if (loadError) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Asistenta se nepodařilo načíst - zkontroluj připojení k internetu a zkus to znovu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 64.dp),
                    )
                    Button(
                        onClick = { loadError = false; loading = true; webView.reload() },
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text("Zkusit znovu")
                    }
                }
            }
        }
    }
}
