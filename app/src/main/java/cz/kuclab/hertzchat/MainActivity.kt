package cz.kuclab.hertzchat

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import cz.kuclab.hertzchat.crypto.IdentityKeyManager
import cz.kuclab.hertzchat.p2p.P2pForegroundService
import cz.kuclab.hertzchat.ui.navigation.HertzNavHost
import cz.kuclab.hertzchat.ui.theme.HertzChatTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var identityKeyManager: IdentityKeyManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        startP2pServiceIfReady()
        setContent {
            HertzChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HertzNavHost()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startP2pServiceIfReady()
    }

    private fun startP2pServiceIfReady() {
        if (!identityKeyManager.hasIdentity) return
        val intent = Intent(this, P2pForegroundService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
