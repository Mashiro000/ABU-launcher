package com.limi.tvdesktop.player

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Bridges HTTPS media through Android's TLS stack for libmpv builds whose bundled
 * FFmpeg cannot negotiate with some Emby/Jellyfin/WebDAV servers.
 */
internal class MpvHttpProxy {
    private val server = ServerSocket(0, 16, InetAddress.getByName(LOOPBACK))
    private val client = unsafeHttpClient()
    @Volatile private var targetUrl: String = ""
    @Volatile private var closed = false

    init {
        Thread({ acceptLoop() }, "mpv-http-proxy").apply {
            isDaemon = true
            start()
        }
    }

    fun urlFor(url: String): String {
        targetUrl = url
        return "http://$LOOPBACK:${server.localPort}/stream"
    }

    private fun acceptLoop() {
        while (!closed) {
            try {
                val socket = server.accept()
                Thread({ handle(socket) }, "mpv-http-client").apply {
                    isDaemon = true
                    start()
                }
            } catch (_: SocketException) {
                if (!closed) Log.w(TAG, "本地代理连接中断")
            } catch (error: Throwable) {
                if (!closed) Log.e(TAG, "本地代理接收失败", error)
            }
        }
    }

    private fun handle(socket: Socket) = socket.use { connection ->
        connection.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.ISO_8859_1))
        val requestLine = reader.readLine().orEmpty()
        val method = requestLine.substringBefore(' ').uppercase()
        var range: String? = null
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", ignoreCase = true)) range = line.substringAfter(':').trim()
        }

        val upstreamUrl = targetUrl
        if ((method != "GET" && method != "HEAD") || upstreamUrl.isBlank()) {
            writeSimpleResponse(connection, 400, "Bad Request")
            return@use
        }

        try {
            val request = Request.Builder()
                .url(upstreamUrl)
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android TV) LimiTV")
                .apply { range?.takeIf(String::isNotBlank)?.let { header("Range", it) } }
                .method(method, null)
                .build()
            client.newCall(request).execute().use { response ->
                val output = connection.getOutputStream()
                val reason = response.message.ifBlank { if (response.isSuccessful) "OK" else "Upstream Error" }
                output.write("HTTP/1.1 ${response.code} $reason\r\n".toByteArray(Charsets.ISO_8859_1))
                listOf("Content-Type", "Content-Length", "Content-Range", "Accept-Ranges", "Last-Modified", "ETag")
                    .forEach { name ->
                        response.header(name)?.let { value ->
                            output.write("$name: $value\r\n".toByteArray(Charsets.ISO_8859_1))
                        }
                    }
                output.write("Connection: close\r\n\r\n".toByteArray(Charsets.ISO_8859_1))
                if (method == "GET") response.body?.byteStream()?.copyTo(output, DEFAULT_BUFFER_SIZE)
                output.flush()
            }
        } catch (error: Throwable) {
            // MPV closes the old Range connection whenever it seeks to MKV cues. This is
            // expected and must not be reported as an upstream failure.
            val disconnected = error is SocketException ||
                error.message?.contains("reset", ignoreCase = true) == true ||
                error.message?.contains("broken pipe", ignoreCase = true) == true
            if (!disconnected) Log.e(TAG, "上游媒体请求失败: ${error.message}")
            runCatching { writeSimpleResponse(connection, 502, "Bad Gateway") }
        }
    }

    private fun writeSimpleResponse(socket: Socket, code: Int, reason: String) {
        socket.getOutputStream().apply {
            write("HTTP/1.1 $code $reason\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            flush()
        }
    }

    fun close() {
        closed = true
        runCatching { server.close() }
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
    }

    private companion object {
        const val TAG = "MpvHttpProxy"
        const val LOOPBACK = "127.0.0.1"

        fun unsafeHttpClient(): OkHttpClient {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            }
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
            }
            return OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .build()
        }
    }
}
