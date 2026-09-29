package com.limi.tvdesktop.player

import android.content.Context
import java.security.MessageDigest

object PlayerEngineHistory {
    enum class Engine { MEDIA3, MPV }

    private const val PREFS = "player_engine_history"

    fun preferred(context: Context, mediaKey: String): Engine? {
        val value = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(mediaKey), null)
        return value?.let { runCatching { Engine.valueOf(it) }.getOrNull() }
    }

    fun recordSuccess(context: Context, mediaKey: String, engine: Engine) {
        if (mediaKey.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(mediaKey), engine.name)
            .putLong("seen.${key(mediaKey)}", System.currentTimeMillis())
            .apply()
    }

    fun recordFailure(context: Context, mediaKey: String, engine: Engine) {
        if (preferred(context, mediaKey) == engine) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(key(mediaKey)).apply()
        }
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()

    private fun key(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
}
