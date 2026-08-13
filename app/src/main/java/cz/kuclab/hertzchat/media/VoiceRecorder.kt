package cz.kuclab.hertzchat.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** Records a short voice message to a private temp file (AAC/M4A) at a solid default quality; the file is deleted right after it's handed off to be sent. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun start(): File {
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file
        @Suppress("DEPRECATION")
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        recorder = mediaRecorder
        startedAtMs = System.currentTimeMillis()
        return file
    }

    /** Returns the recorded file and its duration, or null if nothing was recorded. */
    fun stop(): Pair<File, Long>? {
        val mediaRecorder = recorder ?: return null
        val file = outputFile ?: return null
        return try {
            mediaRecorder.stop()
            file to (System.currentTimeMillis() - startedAtMs)
        } catch (_: Exception) {
            null
        } finally {
            mediaRecorder.release()
            recorder = null
        }
    }

    fun cancel() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            // ignore - recorder may not have produced any data yet
        }
        recorder?.release()
        recorder = null
        outputFile?.delete()
    }
}
