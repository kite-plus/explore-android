package plus.kite.explore

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * A tiny HTTP server on the loopback address for testing the API client against real
 * connections. The JDK's own server is not on the Android unit test classpath.
 */
class FakeServer(private val handler: (Request) -> Response) : AutoCloseable {
    class Request(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
        fun header(name: String): String? = headers[name.lowercase()]
    }

    class Response(val status: Int, val body: String = "", val headers: List<Pair<String, String>> = emptyList())

    private val socket = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    val requests = CopyOnWriteArrayList<Request>()
    val url: String get() = "http://127.0.0.1:${socket.localPort}"

    init {
        thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (_: Exception) {
                    break
                }
                client.use {
                    val input = BufferedInputStream(it.getInputStream())
                    val request = read(input) ?: return@use
                    requests += request
                    val response = handler(request)
                    val body = response.body.encodeToByteArray()
                    val head = StringBuilder("HTTP/1.1 ${response.status} X\r\n")
                    head.append("Content-Length: ${body.size}\r\nConnection: close\r\n")
                    if (response.headers.none { (name, _) -> name.equals("Content-Type", true) }) {
                        head.append("Content-Type: application/json\r\n")
                    }
                    response.headers.forEach { (name, value) -> head.append("$name: $value\r\n") }
                    head.append("\r\n")
                    it.getOutputStream().apply {
                        write(head.toString().encodeToByteArray())
                        write(body)
                        flush()
                    }
                }
            }
        }
    }

    private fun read(input: InputStream): Request? {
        val lines = mutableListOf<String>()
        val line = ByteArrayOutputStream()
        while (true) {
            val byte = input.read()
            if (byte < 0) return null
            if (byte == '\n'.code) {
                val text = line.toString(Charsets.UTF_8).trimEnd('\r')
                line.reset()
                if (text.isEmpty()) break
                lines += text
            } else {
                line.write(byte)
            }
        }
        val (method, path) = lines.first().split(' ').let { it[0] to it[1] }
        val headers = lines.drop(1).associate { it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim() }
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        val body = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(body, read, length - read)
            if (count < 0) break
            read += count
        }
        return Request(method, path, headers, body.decodeToString())
    }

    override fun close() = socket.close()
}
