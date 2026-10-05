package com.limi.tvdesktop

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class AppRelease(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
)

internal sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val version: String) : UpdateCheckState
    data class Available(val release: AppRelease) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

/** GitHub Releases based application update checker. It only opens the release page; APK install stays user-controlled. */
internal object AppUpdateManager {
    // /releases/latest omits prereleases, including the 0.04 beta series.
    private const val RELEASE_API = "https://api.github.com/repos/Mashiro000/ABU-launcher/releases?per_page=10"
    private const val RELEASES_PAGE = "https://github.com/Mashiro000/ABU-launcher/releases"
    private const val MAX_RESPONSE_BYTES = 512L * 1024L

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    var state: UpdateCheckState by mutableStateOf(UpdateCheckState.Idle)
        private set

    suspend fun check(context: Context) {
        if (state is UpdateCheckState.Checking) return
        state = UpdateCheckState.Checking
        val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        state = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(
                    Request.Builder()
                        .url(RELEASE_API)
                        .header("Accept", "application/vnd.github+json")
                        .header("X-GitHub-Api-Version", "2022-11-28")
                        .header("User-Agent", "ABU-Launcher/$currentVersion")
                        .build()
                ).execute().use { response ->
                    require(response.isSuccessful) { "GitHub 请求失败（HTTP ${response.code}）" }
                    val body = requireNotNull(response.body) { "GitHub 返回内容为空" }
                    require(body.contentLength() <= MAX_RESPONSE_BYTES || body.contentLength() < 0) { "更新信息过大" }
                    val text = body.charStream().use { reader ->
                        val result = StringBuilder()
                        val buffer = CharArray(4096)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(result.length + count <= MAX_RESPONSE_BYTES) { "更新信息过大" }
                            result.append(buffer, 0, count)
                        }
                        result.toString()
                    }
                    val releases = JSONArray(text)
                    val json = (0 until releases.length())
                        .map { releases.getJSONObject(it) }
                        .filterNot { it.optBoolean("draft", false) }
                        .maxWithOrNull { left, right ->
                            when {
                                isNewer(left.getString("tag_name"), right.getString("tag_name")) -> 1
                                isNewer(right.getString("tag_name"), left.getString("tag_name")) -> -1
                                else -> 0
                            }
                        } ?: error("没有可用的 GitHub 发行版")
                    val release = AppRelease(
                        version = json.getString("tag_name").trim(),
                        title = json.optString("name").takeIf { it.isNotBlank() }
                            ?: json.getString("tag_name"),
                        notes = json.optString("body").trim().ifBlank { "本次发布未提供更新说明。" },
                        pageUrl = json.optString("html_url", RELEASES_PAGE).takeIf { it.startsWith("https://github.com/") }
                            ?: RELEASES_PAGE,
                    )
                    if (isNewer(release.version, currentVersion)) {
                        UpdateCheckState.Available(release)
                    } else {
                        UpdateCheckState.UpToDate(currentVersion)
                    }
                }
            }.getOrElse { UpdateCheckState.Failed(it.message ?: "检查更新失败") }
        }
    }

    fun dismissAvailable() {
        val release = (state as? UpdateCheckState.Available)?.release ?: return
        state = UpdateCheckState.UpToDate("已忽略 ${release.version}")
    }

    fun openReleasePage(context: Context, release: AppRelease) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    internal fun isNewer(remote: String, local: String): Boolean {
        fun core(value: String) = value.removePrefix("v").substringBefore('-')
        fun parts(value: String) = Regex("\\d+").findAll(core(value)).map { it.value.toIntOrNull() ?: 0 }.toList()
        val remoteParts = parts(remote)
        val localParts = parts(local)
        val size = maxOf(remoteParts.size, localParts.size)
        repeat(size) { index ->
            val comparison = (remoteParts.getOrElse(index) { 0 }).compareTo(localParts.getOrElse(index) { 0 })
            if (comparison != 0) return comparison > 0
        }
        // The numeric core is equal: stable beats prerelease, then compare alpha < beta < rc and its sequence.
        fun preRelease(value: String): Pair<Int, Int>? {
            val suffix = value.removePrefix("v").substringAfter('-', "")
            if (suffix.isBlank()) return null
            val rank = when {
                suffix.contains("alpha", true) -> 0
                suffix.contains("beta", true) -> 1
                suffix.contains("rc", true) -> 2
                else -> 0
            }
            val sequence = Regex("\\d+").find(suffix)?.value?.toIntOrNull() ?: 0
            return rank to sequence
        }
        val remotePre = preRelease(remote)
        val localPre = preRelease(local)
        if (remotePre == null || localPre == null) return remotePre == null && localPre != null
        return remotePre.first > localPre.first ||
            (remotePre.first == localPre.first && remotePre.second > localPre.second)
    }
}
