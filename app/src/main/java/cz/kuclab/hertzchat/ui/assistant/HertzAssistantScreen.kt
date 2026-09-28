package cz.kuclab.hertzchat.ui.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Message
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import cz.kuclab.hertzchat.ui.common.GlassBar
import cz.kuclab.hertzchat.ui.common.GlassCircleButton

/** The KucLab Hertz web assistant, embedded as a WebView so it never leaves the app. Trailing slash on purpose - the server 301s the bare path here. */
const val HERTZ_ASSISTANT_URL = "https://kuclab.org/hertz/"

/** Stable synthetic id for the assistant's row in the chat list - it isn't a real contact. */
const val HERTZ_ASSISTANT_CONTACT_ID = "hertz-ai-assistant"

/**
 * The Hertz AI assistant (kuclab.org/hertz) running inside the app. The user signs in
 * with their KucLab account right here and the session cookie persists, so the login
 * survives restarts just like in a browser. Nothing ever leaves the app - every link
 * and login popup loads in this same WebView, which is also what keeps the auth
 * session in one place instead of splitting it with an external browser.
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
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportMultipleWindows(true)
            // First-party page: the agent talks to kuclab.org over plain ws:// from an
            // https:// page, which WebView blocks as mixed content by default - the chat
            // then renders but never connects, looking exactly like a frozen frame.
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // Spoken replies start on their own once the agent answers.
            settings.mediaPlaybackRequiresUserGesture = false
            // The assistant is a stateful web app (login session, credits, history) -
            // without persistent cookies the user would have to sign in on every visit.
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    // Everything stays in-app: sending the login flow to an external
                    // browser strands the session there and this view freezes behind it.
                    return false
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

                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    // A dead renderer otherwise looks like a frozen page with no way
                    // out - surface the retry UI instead, reload recovers from here.
                    if (detail.didCrash()) {
                        loading = false
                        loadError = true
                    }
                    return true
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

                override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
                    // Login buttons that target="_blank" would otherwise open a popup
                    // that goes nowhere - route the popup's URL into this same view.
                    val helper = WebView(view.context).apply {
                        settings.javaScriptEnabled = true
                    }
                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                        helper.destroy()
                        return false
                    }
                    helper.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(popup: WebView, request: WebResourceRequest): Boolean {
                            view.loadUrl(request.url.toString())
                            popup.destroy()
                            return true
                        }
                    }
                    transport.webView = helper
                    resultMsg.sendToTarget()
                    return true
                }
            }
            loadUrl(HERTZ_ASSISTANT_URL)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else onBack()
    }

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        Box(modifier = Modifier.fillMaxSize().padding(top = 68.dp)) {
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
        GlassBar(
            title = "Hertz AI",
            hazeState = null,
            onBack = onBack,
            actions = {
                GlassCircleButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = "Obnovit",
                    onClick = { loadError = false; loading = true; webView.reload() },
                )
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
        }
    }
}
