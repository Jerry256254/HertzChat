package cz.kuclab.hertzchat.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.security.SecureRandom

/**
 * Contract for the large-attachment path: [FileChunks] must slice a file into
 * byte-exact chunks whose concatenation equals the original, and the sender's
 * control-payload count must match what the reader will produce - a mismatch
 * silently truncates a video or wedges the receiver waiting for chunks that
 * never come.
 */
class FileChunksTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `chunk count rounds up partial tail`() {
        assertEquals(0, FileChunks.chunkCount(0, 16 * 1024))
        assertEquals(1, FileChunks.chunkCount(1, 16 * 1024))
        assertEquals(1, FileChunks.chunkCount(16 * 1024, 16 * 1024))
        assertEquals(2, FileChunks.chunkCount(16 * 1024 + 1, 16 * 1024))
        assertEquals(7, FileChunks.chunkCount(100_000, 16 * 1024))
    }

    @Test
    fun `reader concatenates back to the original bytes`() {
        val original = ByteArray(100_000).also { SecureRandom().nextBytes(it) }
        val file = temp.newFile("big.bin").apply { writeBytes(original) }
        FileChunks.Reader(file, MediaCrypto.CHUNK_SIZE).use { reader ->
            assertEquals(FileChunks.chunkCount(file.length(), MediaCrypto.CHUNK_SIZE), reader.chunkCount)
            val rebuilt = ByteArrayOutputStream()
            repeat(reader.chunkCount) { rebuilt.write(reader.readChunk(it)) }
            assertArrayEquals(original, rebuilt.toByteArray())
        }
    }

    @Test
    fun `reader handles exact multiples with no phantom tail`() {
        val original = ByteArray(2 * MediaCrypto.CHUNK_SIZE) { it.toByte() }
        val file = temp.newFile("exact.bin").apply { writeBytes(original) }
        FileChunks.Reader(file, MediaCrypto.CHUNK_SIZE).use { reader ->
            assertEquals(2, reader.chunkCount)
            assertEquals(MediaCrypto.CHUNK_SIZE, reader.readChunk(1).size)
            assertArrayEquals(original, reader.readChunk(0) + reader.readChunk(1))
        }
    }

    @Test
    fun `chunks survive the encrypt-decrypt round trip indexed`() {
        val original = ByteArray(40_000).also { SecureRandom().nextBytes(it) }
        val file = temp.newFile("crypto.bin").apply { writeBytes(original) }
        val key = MediaCrypto.generateKey()
        val salt = MediaCrypto.generateNonceSalt()
        FileChunks.Reader(file, MediaCrypto.CHUNK_SIZE).use { reader ->
            val rebuilt = ByteArrayOutputStream()
            repeat(reader.chunkCount) { index ->
                val cipher = MediaCrypto.encryptChunk(key, salt, index, reader.readChunk(index))
                rebuilt.write(MediaCrypto.decryptChunk(key, salt, index, cipher))
            }
            assertArrayEquals(original, rebuilt.toByteArray())
        }
    }

    @Test
    fun `reading past end of file throws instead of sending garbage`() {
        val file = temp.newFile("small.bin").apply { writeBytes(ByteArray(10)) }
        FileChunks.Reader(file, MediaCrypto.CHUNK_SIZE).use { reader ->
            assertEquals(1, reader.chunkCount)
            assertTrue(runCatching { reader.readChunk(1) }.isFailure)
        }
    }
}
