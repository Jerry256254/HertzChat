package cz.kuclab.hertzchat.network.tor

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

private const val MAX_FRAME_SIZE = 32 * 1024 * 1024 // generous ceiling against a malicious/corrupt length prefix

/** A Tor onion-service TCP stream is reliable and ordered but has no built-in message boundaries, so every frame gets a 4-byte length prefix. */
class TorConnection(private val socket: Socket) {
    private val input = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    @Synchronized
    fun send(bytes: ByteArray) {
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    /** Blocks until a full frame arrives. Throws (typically java.io.EOFException/IOException) when the connection closes. */
    fun receive(): ByteArray {
        val size = input.readInt()
        require(size in 0..MAX_FRAME_SIZE) { "Implausible frame size $size" }
        val bytes = ByteArray(size)
        input.readFully(bytes)
        return bytes
    }

    fun close() {
        runCatching { socket.close() }
    }
}
