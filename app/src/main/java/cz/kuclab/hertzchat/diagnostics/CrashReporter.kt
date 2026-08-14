package cz.kuclab.hertzchat.diagnostics

import android.content.Context
import android.os.Build
import android.os.Looper
import cz.kuclab.hertzchat.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the stack trace of a crash so the next launch can show it.
 *
 * Without this, a crash on a device that isn't attached to a debugger leaves
 * nothing behind but the system's "app keeps stopping" dialog - the trace is
 * in logcat, which needs USB debugging to read. Writing it to the app's own
 * private storage means the report survives the process dying and can be
 * read (and copied out) from inside the app itself.
 */
object CrashReporter {

    private const val FILE_NAME = "last_crash.txt"

    /** Errors the app survived (see install) - kept for diagnosis, never shown as a crash. */
    private const val ROUTER_FILE_NAME = "last_router_error.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext ?: context
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // The embedded I2P router runs a crowd of its own background threads, and an
            // uncaught exception on any thread normally takes the entire process with it -
            // so one misbehaving corner of the router (its bundled UPnP/SSDP stack, for
            // one) would kill a chat app that is otherwise working fine. Losing that one
            // thread is survivable; losing the app isn't.
            val survivable = thread !== Looper.getMainLooper().thread && isFromEmbeddedRouter(error)

            // Best-effort: a failure while recording the crash must never replace
            // the original one, or the real cause is lost for good. Survived errors go
            // to a separate file so the "app crashed" dialog only ever means it really did,
            // while the evidence is still on disk if the router starts misbehaving.
            runCatching { write(appContext, thread, error, if (survivable) ROUTER_FILE_NAME else FILE_NAME) }

            if (survivable) return@setDefaultUncaughtExceptionHandler
            previous?.uncaughtException(thread, error)
        }
    }

    private fun isFromEmbeddedRouter(error: Throwable): Boolean {
        var cause: Throwable? = error
        while (cause != null) {
            if (cause.stackTrace.any { it.className.startsWith("net.i2p.") || it.className.startsWith("org.cybergarage.") }) return true
            cause = cause.cause.takeIf { it !== cause }
        }
        return false
    }

    private fun write(context: Context, thread: Thread, error: Throwable, fileName: String) {
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val when_ = SimpleDateFormat("d. M. yyyy HH:mm:ss", Locale.getDefault()).format(Date())
        val report = buildString {
            appendLine("Hertz Chat ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Vlákno: ${thread.name}")
            appendLine(when_)
            appendLine()
            append(stack)
        }
        File(context.filesDir, fileName).writeText(report)
    }

    fun lastCrash(context: Context): String? =
        File(context.filesDir, FILE_NAME).takeIf { it.exists() }?.runCatching { readText() }?.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
