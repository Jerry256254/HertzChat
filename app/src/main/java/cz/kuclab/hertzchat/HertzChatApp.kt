package cz.kuclab.hertzchat

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import cz.kuclab.hertzchat.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HertzChatApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Installed here rather than in onCreate() so that a crash during Hilt's own
    // startup injection - which runs inside the generated onCreate(), before any
    // of our code there would get a chance to run - is still recorded.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashReporter.install(this)
        // One-way cleanup of the removed Mistral assistant: its encrypted prefs held the
        // user's API keys, and nothing reads them anymore. No-op when already gone.
        deleteSharedPreferences("hertzchat_mistral_prefs")
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
