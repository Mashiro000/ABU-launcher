package com.limi.tvdesktop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal data class HomeFolder(val id: String, val name: String, val members: List<String>)
internal data class HomeWidget(val id: Int, val provider: String, val widthDp: Int, val heightDp: Int, val xDp: Int = 36, val yDp: Int = 36)
internal data class AppLaunchUsage(val count: Int, val lastUsedAt: Long)

/** Small, local launcher state keyed by package/activity identity. */
internal object AppHomeFeatures {
    private const val PREFS = "home_app_features"
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun hidden(context: Context): Set<String> = readSet(context, "hidden")
    fun locked(context: Context): Set<String> = readSet(context, "locked")
    private fun readSet(context: Context, key: String): Set<String> =
        runCatching { JSONArray(prefs(context).getString(key, "[]")).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }.getOrDefault(emptySet())

    private fun writeSet(context: Context, key: String, values: Set<String>) {
        prefs(context).edit().putString(key, JSONArray(values.toList()).toString()).apply()
    }

    fun setHidden(context: Context, key: String, hidden: Boolean) = writeSet(context, "hidden", hidden(context).let { if (hidden) it + key else it - key })
    fun setLocked(context: Context, key: String, locked: Boolean) = writeSet(context, "locked", this.locked(context).let { if (locked) it + key else it - key })

    fun homeOrder(context: Context): List<String> = runCatching {
        val array = JSONArray(prefs(context).getString("home_order", "[]"))
        (0 until array.length()).map(array::getString)
    }.getOrDefault(emptyList())

    fun saveHomeOrder(context: Context, keys: List<String>) {
        prefs(context).edit().putString("home_order", JSONArray(keys.distinct()).toString()).apply()
    }

    fun launchUsage(context: Context, key: String): AppLaunchUsage {
        val id = stableKey(key)
        return AppLaunchUsage(
            prefs(context).getInt("launch_count_$id", 0),
            prefs(context).getLong("launch_last_$id", 0L)
        )
    }

    fun recordLaunch(context: Context, key: String, now: Long = System.currentTimeMillis()) {
        val id = stableKey(key)
        val count = prefs(context).getInt("launch_count_$id", 0)
        prefs(context).edit().putInt("launch_count_$id", count + 1).putLong("launch_last_$id", now).apply()
    }

    private fun stableKey(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    fun customLabel(context: Context, key: String): String? = prefs(context).getString("label_${stableKey(key)}", null)
    fun saveCustomLabel(context: Context, key: String, label: String?) {
        prefs(context).edit().apply {
            if (label.isNullOrBlank()) remove("label_${stableKey(key)}") else putString("label_${stableKey(key)}", label.trim())
        }.apply()
    }

    private fun customIconFile(context: Context, key: String) = File(context.filesDir, "launcher_icon_${stableKey(key)}.png")
    fun customIcon(context: Context, key: String): ImageBitmap? = runCatching {
        BitmapFactory.decodeFile(customIconFile(context, key).absolutePath)?.asImageBitmap()
    }.getOrNull()

    fun saveCustomIcon(context: Context, key: String, uri: Uri): Boolean = runCatching {
        val decoded = context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream) ?: return false
        val scaled = if (decoded.width > 512 || decoded.height > 512) {
            val scale = minOf(512f / decoded.width, 512f / decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt().coerceAtLeast(1), (decoded.height * scale).toInt().coerceAtLeast(1), true)
        } else decoded
        customIconFile(context, key).outputStream().use { output ->
            check(scaled.compress(Bitmap.CompressFormat.PNG, 100, output))
        }
        if (scaled !== decoded) scaled.recycle()
        decoded.recycle()
        true
    }.getOrDefault(false)

    fun clearCustomIcon(context: Context, key: String) { runCatching { customIconFile(context, key).delete() } }

    private const val PIN_KIND_DIRECTION = "direction_v1"

    fun hasPin(context: Context): Boolean =
        prefs(context).getString("pin_kind", null) == PIN_KIND_DIRECTION &&
            prefs(context).contains("pin_hash") && prefs(context).getInt("pin_length", 0) >= 4

    fun pinLength(context: Context): Int = if (hasPin(context)) prefs(context).getInt("pin_length", 0) else 0

    fun setPin(context: Context, pin: String) {
        val salt = ByteArray(16).also(java.security.SecureRandom()::nextBytes)
        prefs(context).edit()
            .putString("pin_kind", PIN_KIND_DIRECTION)
            .putInt("pin_length", pin.length)
            .putString("pin_salt", salt.joinToString("") { "%02x".format(it) })
            .putString("pin_hash", hash(salt, pin))
            .apply()
    }

    fun matchesPin(context: Context, pin: String): Boolean = runCatching {
        val saltHex = prefs(context).getString("pin_salt", null) ?: return false
        val expected = prefs(context).getString("pin_hash", null) ?: return false
        val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        MessageDigest.isEqual(expected.toByteArray(), hash(salt, pin).toByteArray())
    }.getOrDefault(false)

    private fun hash(salt: ByteArray, pin: String) =
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray()).joinToString("") { "%02x".format(it) }

    fun folders(context: Context): List<HomeFolder> = runCatching {
        val array = JSONArray(prefs(context).getString("folders", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            val members = item.getJSONArray("members")
            HomeFolder(item.getString("id"), item.getString("name"), (0 until members.length()).map(members::getString))
        }
    }.getOrDefault(emptyList())

    fun saveFolder(context: Context, folder: HomeFolder) {
        val updated = folders(context).filterNot { it.id == folder.id } + folder
        saveFolders(context, updated)
    }

    fun saveFolders(context: Context, folders: List<HomeFolder>) {
        val array = JSONArray()
        folders.forEach { f -> array.put(JSONObject().put("id", f.id).put("name", f.name).put("members", JSONArray(f.members))) }
        prefs(context).edit().putString("folders", array.toString()).apply()
    }

    /** Moves keys between folders atomically. Source folders with one app left dissolve. */
    fun moveIntoFolder(context: Context, targetId: String, keys: List<String>) {
        val moving = keys.toSet()
        val current = folders(context)
        val target = current.firstOrNull { it.id == targetId } ?: return
        val order = homeOrder(context).toMutableList()
        val targetKey = "folder.$targetId/$targetId"
        val targetIndex = order.indexOf(targetKey)
        val result = buildList {
            current.forEach { folder ->
                if (folder.id == targetId) return@forEach
                val remaining = folder.members.filterNot(moving::contains)
                val folderKey = "folder.${folder.id}/${folder.id}"
                when {
                    remaining.isEmpty() -> order.remove(folderKey)
                    remaining.size == 1 && folder.members.size > 1 -> {
                        val index = order.indexOf(folderKey)
                        if (index >= 0) order[index] = remaining.first()
                    }
                    else -> add(folder.copy(members = remaining))
                }
            }
            add(target.copy(members = (target.members + keys).distinct()))
        }
        order.removeAll(moving)
        if (targetIndex >= 0) {
            order.remove(targetKey)
            order.add(targetIndex.coerceAtMost(order.size), targetKey)
        }
        saveFolders(context, result)
        saveHomeOrder(context, order)
    }

    fun widgets(context: Context): List<HomeWidget> = runCatching {
        val array = JSONArray(prefs(context).getString("widgets", "[]"))
        (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            HomeWidget(item.getInt("id"), item.getString("provider"), item.getInt("width"), item.getInt("height"), item.optInt("x", 36), item.optInt("y", 36))
        }
    }.getOrDefault(emptyList())

    fun saveWidgets(context: Context, widgets: List<HomeWidget>) {
        val array = JSONArray()
        widgets.forEach { widget ->
            array.put(JSONObject().put("id", widget.id).put("provider", widget.provider).put("width", widget.widthDp)
                .put("height", widget.heightDp).put("x", widget.xDp).put("y", widget.yDp))
        }
        prefs(context).edit().putString("widgets", array.toString()).apply()
    }
}
