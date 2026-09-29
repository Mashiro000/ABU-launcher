package com.limi.tvdesktop.plugins.runtime

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class PluginSandboxClient(private val context: Context) : Closeable {
    private val ids = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Result<String>>>()
    private var remote: Messenger? = null
    private var bound = false
    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        val data = message.data
        val deferred = pending.remove(data.getLong(PluginRuntimeProtocol.KEY_REQUEST_ID)) ?: return@Handler true
        val error = data.getString(PluginRuntimeProtocol.KEY_ERROR)
        deferred.complete(if (error == null) Result.success(data.getString(PluginRuntimeProtocol.KEY_RESULT) ?: "null") else Result.failure(IllegalStateException(error)))
        true
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { remote = Messenger(service) }
        override fun onServiceDisconnected(name: ComponentName?) { remote = null }
    }

    suspend fun execute(pluginId: String, source: String, method: String, inputJson: String = "{}", timeoutMs: Long = 2_000): Result<String> =
        withContext(Dispatchers.Main) {
            runCatching {
                ensureBound()
                withTimeout(timeoutMs) {
                    while (remote == null) kotlinx.coroutines.delay(10)
                    val id = ids.incrementAndGet()
                    val deferred = CompletableDeferred<Result<String>>()
                    pending[id] = deferred
                    remote!!.send(Message.obtain(null, PluginRuntimeProtocol.EXECUTE).apply {
                        replyTo = replies
                        data = Bundle().apply {
                            putLong(PluginRuntimeProtocol.KEY_REQUEST_ID, id)
                            putString(PluginRuntimeProtocol.KEY_PLUGIN_ID, pluginId)
                            putString(PluginRuntimeProtocol.KEY_SOURCE, source)
                            putString(PluginRuntimeProtocol.KEY_METHOD, method)
                            putString(PluginRuntimeProtocol.KEY_INPUT, inputJson)
                        }
                    })
                    deferred.await().getOrThrow()
                }
            }.onFailure { reset() }
        }

    private fun ensureBound() {
        if (bound) return
        bound = context.bindService(Intent(context, PluginSandboxService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "无法启动插件隔离进程" }
    }

    private fun reset() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false
        remote = null
        pending.values.forEach { it.complete(Result.failure(IllegalStateException("插件进程已重置"))) }
        pending.clear()
    }

    override fun close() = reset()
}
