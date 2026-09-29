package com.limi.tvdesktop.plugins.runtime

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import app.cash.quickjs.QuickJs
import java.util.concurrent.Executors

/** Executes untrusted JavaScript in an Android isolated process without app permissions. */
class PluginSandboxService : Service() {
    private val worker = Executors.newSingleThreadExecutor()
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what != PluginRuntimeProtocol.EXECUTE) return@Handler false
        val request = Bundle(message.data)
        val reply = message.replyTo
        worker.execute {
            val response = Message.obtain(null, PluginRuntimeProtocol.RESULT).apply {
                data = execute(request)
            }
            runCatching { reply.send(response) }
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun execute(request: Bundle): Bundle {
        val requestId = request.getLong(PluginRuntimeProtocol.KEY_REQUEST_ID)
        return Bundle().apply {
            putLong(PluginRuntimeProtocol.KEY_REQUEST_ID, requestId)
            runCatching {
                val source = requireNotNull(request.getString(PluginRuntimeProtocol.KEY_SOURCE))
                val method = request.getString(PluginRuntimeProtocol.KEY_METHOD).orEmpty()
                require(method.matches(Regex("[A-Za-z][A-Za-z0-9_]{0,63}"))) { "无效方法" }
                val input = request.getString(PluginRuntimeProtocol.KEY_INPUT) ?: "{}"
                val script = """
                    "use strict";
                    $source
                    (() => {
                      const api = globalThis.ABUPlugin;
                      if (!api || typeof api[${jsString(method)}] !== "function") throw new Error("plugin method missing");
                      const value = api[${jsString(method)}](JSON.parse(${jsString(input)}));
                      if (value && typeof value.then === "function") throw new Error("async plugin methods are not supported");
                      return JSON.stringify(value ?? null);
                    })()
                """.trimIndent()
                val result = QuickJs.create().use { it.evaluate(script, "plugin.js") }?.toString() ?: "null"
                require(result.length <= PluginRuntimeProtocol.MAX_RESULT_CHARS) { "插件返回内容过大" }
                putString(PluginRuntimeProtocol.KEY_RESULT, result)
            }.onFailure { putString(PluginRuntimeProtocol.KEY_ERROR, it.message ?: it.javaClass.simpleName) }
        }
    }

    private fun jsString(value: String): String = org.json.JSONObject.quote(value)
}
