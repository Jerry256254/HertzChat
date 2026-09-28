package cz.kuclab.hertzchat.media

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

/**
 * Off-disk chunk reads for large attachments. Sending a video or APK must hold
 * one chunk in RAM instead of the whole file - this is the reader both the
 * sender and the chunk-count math use, so the two can never diverge. Pure
 * `java.io` with no Android dependency, so it unit-tests on plain JVM.
 */
object FileChunks {
    /** How many [chunkSize] slices [fileLength] bytes split into (0 for empty). */
    fun chunkCount(fileLength: Long, chunkSize: Int): Int {
        require(chunkSize > 0) { "chunkSize must be positive" }
        if (fileLength <= 0) return 0
        return ((fileLength + chunkSize - 1) / chunkSize).toInt()
    }

    /**
     * Random-access chunk reader: one open handle, any chunk re-readable, so a
     * retried chunk re-reads the same bytes. Must be [close]d (use [.use]).
     */
    class Reader(file: File, private val chunkSize: Int) : Closeable {
        private val raf = RandomAccessFile(file, "r")
        val chunkCount: Int = chunkCount(file.length(), chunkSize)

        /** Reads chunk [index] (0-based); throws past end-of-file. */
        fun readChunk(index: Int): ByteArray {
            require(index >= 0) { "chunk index must be non-negative" }
            raf.seek(index.toLong() * chunkSize)
            val buf = ByteArray(chunkSize)
            var read = 0
            while (read < buf.size) {
                val n = raf.read(buf, read, buf.size - read)
                if (n < 0) break
                read += n
            }
            check(read > 0) { "chunk $index beyond end of file" }
            return if (read == buf.size) buf else buf.copyOf(read)
        }

        override fun close() = raf.close()
    }
}
