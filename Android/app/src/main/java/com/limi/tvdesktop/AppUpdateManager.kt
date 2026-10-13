package com.limi.tvdesktop

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

internal data class AppRelease(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String? = null,
)

internal sealed interface UpdateCheckState {
    data object Idle : UpdateCheckState
    data object Checking : UpdateCheckState
    data class UpToDate(val version: String) : UpdateCheckState
    data class Available(val release: AppRelease) : UpdateCheckState
    data class Downloading(val release: AppRelease) : UpdateCheckState
    data class Ready(val release: AppRelease) : UpdateCheckState
    data class Failed(val message: String) : UpdateCheckState
}

/** GitHub Releases checker with a private APK cache. Installation remains user-controlled. */
internal object AppUpdateManager {
    // /releases/latest omits prereleases, including the 0.04 beta series.
    private const val RELEASE_API = "https://api.github.com/repos/Mashiro000/ABU-launcher/releases?per_page=10"
    private const val RELEASES_PAGE = "https://github.com/Mashiro000/ABU-launcher/releases"
    private const val MAX_RESPONSE_BYTES = 512L * 1024L
    private const val MAX_APK_BYTES = 300L * 1024L * 1024L
    private const val APK_MIME = "application/vnd.android.package-archive"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    private val downloads = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var state: UpdateCheckState by mutableStateOf(UpdateCheckState.Idle)
        private set
    var dialogSuppressed by mutableStateOf(false)
        private set
    fun hidePopup() { dialogSuppressed = true }
    fun checkFromSettings(context: Context) {
        downloads.launch {
            try { check(context) } finally { hidePopup() }
        }
    }
    private var pendingInstallPermission = false

    suspend fun check(context: Context, openCachedInstaller: Boolean = false) {
        if (state is UpdateCheckState.Checking || state is UpdateCheckState.Downloading) return
        dialogSuppressed = false
        state = UpdateCheckState.Checking
        val currentVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
        val result = withContext(Dispatchers.IO) {
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
                        apkUrl = selectApkUrl(json.optJSONArray("assets")),
                    )
                    if (isNewer(release.version, currentVersion)) {
                        UpdateCheckState.Available(release)
                    } else {
                        UpdateCheckState.UpToDate(currentVersion)
                    }
                }
            }.getOrElse { UpdateCheckState.Failed(it.message ?: "检查更新失败") }
        }
        val cached = withContext(Dispatchers.IO) { cachedRelease(context, currentVersion) }
        state = when (result) {
            is UpdateCheckState.Available -> {
                when {
                    cached != null && isNewer(result.release.version, cached.version) -> {
                        withContext(Dispatchers.IO) { clearCache(context) }
                        if (result.release.apkUrl != null) {
                            startDownload(context, result.release, openAfterDownload = openCachedInstaller)
                            return
                        }
                        result
                    }
                    cached != null -> UpdateCheckState.Ready(cached)
                    else -> result
                }
            }
            else -> if (cached != null) UpdateCheckState.Ready(cached) else result
        }
        if (openCachedInstaller && state is UpdateCheckState.Ready) openDownloadedApk(context)
    }

    internal fun selectApkUrl(assets: JSONArray?, supportedAbis: Array<String> = Build.SUPPORTED_ABIS): String? {
        if (assets == null) return null
        val apks = (0 until assets.length()).map { assets.getJSONObject(it) }
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val knownAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        val preferred = apks.firstOrNull { it.optString("name").contains("universal", true) }
            ?: apks.firstOrNull { asset ->
                knownAbis.firstOrNull { asset.optString("name").contains(it, ignoreCase = true) }
                    ?.let { it in supportedAbis } == true
            }
            ?: apks.singleOrNull()?.takeIf { asset ->
                knownAbis.none { asset.optString("name").contains(it, ignoreCase = true) }
            }
        return preferred?.optString("browser_download_url")?.takeIf { it.startsWith("https://github.com/") }
    }

    private fun updateDir(context: Context) = File(context.filesDir, "updates")
    private fun updateFile(context: Context) = File(updateDir(context), "update.apk")
    private fun cachePrefs(context: Context) = context.getSharedPreferences("app_updates", Context.MODE_PRIVATE)

    private fun cachedRelease(context: Context, currentVersion: String): AppRelease? {
        val file = updateFile(context)
        val prefs = cachePrefs(context)
        val version = prefs.getString("version", null) ?: return null
        if (!file.isFile || file.length() == 0L || !isNewer(version, currentVersion)) {
            clearCache(context)
            return null
        }
        return AppRelease(version, prefs.getString("title", version) ?: version,
            prefs.getString("notes", "") ?: "", prefs.getString("pageUrl", RELEASES_PAGE) ?: RELEASES_PAGE,
            prefs.getString("apkUrl", null))
    }

    private fun clearCache(context: Context) {
        updateFile(context).delete()
        File(updateDir(context), "update.part").delete()
        cachePrefs(context).edit().clear().apply()
    }

    fun startDownload(context: Context, release: AppRelease, openAfterDownload: Boolean = false) {
        if (state is UpdateCheckState.Downloading) return
        dialogSuppressed = false
        state = UpdateCheckState.Downloading(release)
        downloads.launch {
            download(context.applicationContext, release)
            if (openAfterDownload && state is UpdateCheckState.Ready) openDownloadedApk(context)
        }
    }

    private suspend fun download(context: Context, release: AppRelease) {
        val url = release.apkUrl ?: run {
            state = UpdateCheckState.Failed("此发行版没有可直接下载的 APK")
            return
        }
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val directory = updateDir(context)
                check(directory.exists() || directory.mkdirs()) { "无法创建下载目录" }
                val part = File(directory, "update.part")
                try {
                    client.newCall(Request.Builder().url(url).header("User-Agent", "ABU-Launcher").build())
                        .execute().use { response ->
                            check(response.isSuccessful) { "下载失败（HTTP ${response.code}）" }
                            val body = requireNotNull(response.body) { "下载内容为空" }
                            check(body.contentLength() in -1..MAX_APK_BYTES) { "安装包过大" }
                            var copied = 0L
                            body.byteStream().use { input -> part.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    copied += count
                                    check(copied <= MAX_APK_BYTES) { "安装包过大" }
                                    output.write(buffer, 0, count)
                                }
                            } }
                        }
                    val archive = context.packageManager.getPackageArchiveInfo(part.absolutePath, 0)
                    check(archive?.packageName == context.packageName) { "安装包不属于当前应用" }
                    val installed = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0"
                    check(isNewer(archive.versionName ?: "0", installed)) { "安装包版本不高于当前版本" }
                    updateFile(context).delete()
                    check(part.renameTo(updateFile(context))) { "保存安装包失败" }
                    cachePrefs(context).edit().putString("version", release.version)
                        .putString("title", release.title).putString("notes", release.notes)
                        .putString("pageUrl", release.pageUrl).putString("apkUrl", release.apkUrl).apply()
                } finally {
                    part.delete()
                }
            }
        }
        state = result.fold({ UpdateCheckState.Ready(release) },
            { UpdateCheckState.Failed(it.message ?: "下载安装包失败") })
    }

    fun openDownloadedApk(context: Context): Boolean {
        val file = updateFile(context).takeIf { it.isFile && it.length() > 0L } ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
                pendingInstallPermission = true
                context.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } else {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
                context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
            }
            true
        }.getOrDefault(false)
    }

    fun resumePendingInstallation(context: Context) {
        if (!pendingInstallPermission) return
        pendingInstallPermission = false
        if (Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()) {
            openDownloadedApk(context)
        }
    }

    fun dismissAvailable() {
        if (state is UpdateCheckState.Downloading) {
            dialogSuppressed = true
            return
        }
        val release = when (val current = state) {
            is UpdateCheckState.Available -> current.release
            is UpdateCheckState.Ready -> current.release
            else -> return
        }
        state = UpdateCheckState.UpToDate("已关闭 ${release.version}")
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
