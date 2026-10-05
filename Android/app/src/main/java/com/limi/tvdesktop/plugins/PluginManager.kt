package com.limi.tvdesktop.plugins

import android.content.Context
import android.system.Os
import org.json.JSONArray
import org.json.JSONObject
import com.limi.tvdesktop.plugins.runtime.PluginServiceRegistry
import com.limi.tvdesktop.plugins.runtime.PluginDeviceEvents
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Owns plugin state and storage. Executable plugins are never unpacked outside the app sandbox.
 * Runtime loading is intentionally handled by kind-specific hosts (player, UI, data source).
 */
class PluginManager private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("plugin_manager", Context.MODE_PRIVATE)
    private val root = File(context.filesDir, "plugins").apply { mkdirs() }

    init {
        recoverInterruptedOperations()
        PluginDeviceEvents.start(context)
        installed().filter(InstalledPlugin::enabled).forEach { plugin ->
            plugin.services.forEach { PluginServiceRegistry.register(plugin.id, it.name, it.version) }
        }
    }

    fun repositories(): List<PluginRepository> {
        val saved = prefs.getString("repositories", null)
        if (saved == null) return listOf(DEFAULT_REPOSITORY)
        return buildList {
            add(DEFAULT_REPOSITORY)
            val array = runCatching { JSONArray(saved) }.getOrNull() ?: return@buildList
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url")
                if (url.isBlank() || url == DEFAULT_REPOSITORY.indexUrl) continue
                add(PluginRepository(item.optString("name", url), url, enabled = item.optBoolean("enabled", true)))
            }
        }
    }

    fun installed(): List<InstalledPlugin> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .mapNotNull(::readInstalled)
        .sortedBy { it.name.lowercase() }

    fun setEnabled(id: String, enabled: Boolean) {
        val dir = safePluginDir(id)
        require(File(dir, "manifest.json").isFile) { "插件不存在" }
        if (enabled) validateInstalledManifest(JSONObject(File(dir, "manifest.json").readText()))
        else PluginServiceRegistry.unregisterPlugin(id)
        prefs.edit().putBoolean("enabled.$id", enabled)
            .also { if (enabled) it.putString("last_enabled_plugin", id) }
            .apply()
        if (enabled) readInstalled(dir)?.services?.forEach { PluginServiceRegistry.register(id, it.name, it.version) }
    }

    fun activateVersion(id: String, version: String): InstalledPlugin {
        val pluginRoot = safePluginDir(id)
        val versionDir = File(pluginRoot, version)
        require(versionDir.isDirectory && File(versionDir, "manifest.json").isFile) { "插件版本不存在" }
        val manifest = JSONObject(File(versionDir, "manifest.json").readText())
        require(manifest.getString("id") == id && manifest.getString("version") == version) { "插件版本清单不匹配" }
        validateInstalledManifest(manifest)
        writeAtomically(File(pluginRoot, "current"), version)
        writeAtomically(File(pluginRoot, "manifest.json"), manifest.toString(2))
        PluginServiceRegistry.unregisterPlugin(id)
        if (prefs.getBoolean("enabled.$id", false)) readInstalled(pluginRoot)?.services?.forEach {
            PluginServiceRegistry.register(id, it.name, it.version)
        }
        return requireNotNull(readInstalled(pluginRoot)) { "插件版本切换失败" }
    }

    fun currentVersionDir(id: String): File? {
        val dir = safePluginDir(id)
        val current = File(dir, "current").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        return File(dir, current).takeIf { current.isNotBlank() && it.isDirectory }
    }

    fun readEntryScript(id: String): String? {
        val plugin = installed().firstOrNull { it.id == id && it.enabled } ?: return null
        val entry = plugin.entry ?: return null
        require(PluginScriptManifestValidator.validPath(entry)) { "插件入口路径无效" }
        val root = currentVersionDir(id) ?: return null
        val file = File(root, entry)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator)) { "插件入口越界" }
        require(file.isFile && file.length() <= MAX_SCRIPT_BYTES) { "插件入口不存在或过大" }
        return file.readText()
    }

    fun addRepository(name: String, indexUrl: String) {
        val uri = java.net.URI(indexUrl.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "插件仓库必须是有效的 HTTPS 地址" }
        val custom = repositories().filterNot { it.official }.toMutableList()
        custom.removeAll { it.indexUrl == indexUrl }
        custom += PluginRepository(name.ifBlank { indexUrl }, indexUrl)
        saveRepositories(custom)
    }

    fun removeRepository(indexUrl: String) {
        saveRepositories(repositories().filterNot { it.official || it.indexUrl == indexUrl })
    }

    fun setRepositoryEnabled(indexUrl: String, enabled: Boolean) {
        require(indexUrl != DEFAULT_REPOSITORY.indexUrl) { "官方仓库不能停用" }
        val custom = repositories().filterNot { it.official }.map {
            if (it.indexUrl == indexUrl) it.copy(enabled = enabled) else it
        }
        require(custom.any { it.indexUrl == indexUrl }) { "仓库不存在" }
        saveRepositories(custom)
    }

    /** Installs a previously downloaded package after hash verification. Keeps three versions. */
    fun installPackage(
        zip: File,
        expectedSha256: String? = null,
        verification: PluginVerification? = null,
    ): InstalledPlugin {
        if (expectedSha256 != null) {
            require(sha256(zip).equals(expectedSha256, ignoreCase = true)) { "插件文件校验失败" }
        }
        if (verification != null) {
            require(PluginSignatureVerifier.verify(zip, verification.publicKeyBase64, verification.signatureBase64)) {
                "插件签名验证失败"
            }
        }
        val staging = File(root, ".staging-${System.nanoTime()}").apply { mkdirs() }
        try {
            unzipSafely(zip, staging)
            val manifestFile = File(staging, "manifest.json")
            require(manifestFile.isFile) { "插件包缺少 manifest.json" }
            val manifest = JSONObject(manifestFile.readText())
            val id = manifest.getString("id")
            val version = manifest.getString("version")
            require(ID_PATTERN.matches(id)) { "插件 ID 无效" }
            require(VERSION_PATTERN.matches(version)) { "插件版本无效" }
            require(manifest.optInt("schemaVersion") == 1) { "不支持的插件清单版本" }
            requireHostApi(manifest)
            val kind = runCatching { PluginKind.valueOf(manifest.getString("kind").uppercase()) }
                .getOrElse { throw IllegalArgumentException("插件类型无效") }
            if (kind != PluginKind.PLAYER && manifest.optString("hostApi").isNotBlank()) {
                PluginScriptManifestValidator.validate(manifest)
                staging.walkTopDown().filter(File::isFile).forEach { file ->
                    val relative = file.relativeTo(staging).invariantSeparatorsPath
                    require(relative == "manifest.json" || relative.startsWith("dist/") || relative.startsWith("assets/")) {
                        "插件包包含不允许的文件：$relative"
                    }
                }
            }
            val entry = manifest.optString("entry")
            val services = manifest.optJSONArray("services") ?: JSONArray()
            for (i in 0 until services.length()) {
                val service = services.optJSONObject(i) ?: throw IllegalArgumentException("服务声明无效")
                require(service.optString("name").matches(Regex("[a-zA-Z0-9._-]{1,100}")) && service.optInt("version") > 0) { "服务声明无效" }
            }
            if (kind != PluginKind.PLAYER) {
                require(staging.walkTopDown().filter(File::isFile).none { it.extension.lowercase() in setOf("so", "dex", "apk") }) {
                    "脚本插件不能包含原生代码或 APK"
                }
                require(entry.isNotBlank() && !entry.startsWith('/') && !entry.contains("..")) { "脚本插件入口无效" }
                val entryFile = File(staging, entry)
                require(entryFile.canonicalPath.startsWith(staging.canonicalPath + File.separator) && entryFile.isFile) { "插件入口不存在" }
                require(entryFile.length() <= MAX_SCRIPT_BYTES) { "插件入口过大" }
            }

            val pluginRoot = safePluginDir(id).apply { mkdirs() }
            val versionDir = File(pluginRoot, version)
            require(!versionDir.exists()) { "此插件版本已经安装" }
            require(staging.renameTo(versionDir)) { "无法保存插件" }
            val trust = when {
                verification?.official == true -> PluginTrust.OFFICIAL
                verification != null -> PluginTrust.VERIFIED
                else -> PluginTrust.UNVERIFIED
            }
            writeAtomically(File(pluginRoot, "manifest.json"), manifest.toString(2))
            writeAtomically(File(pluginRoot, "trust"), trust.name)
            verification?.let { writeAtomically(File(pluginRoot, "publisher"), PluginSignatureVerifier.fingerprint(it.publicKeyBase64)) }
            writeAtomically(File(pluginRoot, "current"), version)
            PluginServiceRegistry.unregisterPlugin(id)
            if (prefs.getBoolean("enabled.$id", false)) readInstalled(pluginRoot)?.services?.forEach {
                PluginServiceRegistry.register(id, it.name, it.version)
            }
            pruneVersions(pluginRoot, version)
            return requireNotNull(readInstalled(pluginRoot))
        } finally {
            if (staging.exists()) staging.deleteRecursively()
        }
    }

    fun uninstall(id: String) {
        PluginServiceRegistry.unregisterPlugin(id)
        val dir = safePluginDir(id)
        if (dir.exists()) dir.deleteRecursively()
        prefs.edit().remove("enabled.$id").apply()
        val permissionPrefs = context.getSharedPreferences("plugin_permissions", Context.MODE_PRIVATE)
        permissionPrefs.edit().also { editor ->
            permissionPrefs.all.keys.filter { it.startsWith("$id.") }.forEach(editor::remove)
        }.apply()
        context.getSharedPreferences("plugin_data_$id", Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun readInstalled(dir: File): InstalledPlugin? = runCatching {
        val manifest = JSONObject(File(dir, "manifest.json").readText())
        val id = manifest.getString("id")
        val versions = dir.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }.map { it.name }
        val current = File(dir, "current").takeIf { it.isFile }?.readText()?.trim().orEmpty()
        val permissionsJson = manifest.optJSONArray("permissions") ?: JSONArray()
        val permissions = (0 until permissionsJson.length()).map { index ->
            val value = permissionsJson.opt(index)
            if (value is JSONObject) PluginPermission(value.getString("id"), value.optString("title", value.getString("id")), value.optBoolean("sensitive"))
            else PluginPermission(value.toString(), value.toString())
        }
        InstalledPlugin(
            id = id,
            name = manifest.optString("name", id),
            version = current.ifBlank { manifest.getString("version") },
            description = manifest.optString("description"),
            kind = runCatching { PluginKind.valueOf(manifest.optString("kind", "SYSTEM").uppercase()) }.getOrDefault(PluginKind.SYSTEM),
            author = manifest.optString("author", "未知发布者"),
            enabled = prefs.getBoolean("enabled.$id", false),
            trust = File(dir, "trust").takeIf { it.isFile }?.readText()?.trim()?.let {
                runCatching { PluginTrust.valueOf(it) }.getOrNull()
            } ?: PluginTrust.UNVERIFIED,
            installedBytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() },
            permissions = permissions,
            availableVersions = versions.sortedWith(::comparePluginVersions).reversed(),
            entry = manifest.optString("entry").takeIf { it.isNotBlank() },
            surfaces = manifest.stringList("surfaces"),
            slots = manifest.stringList("slots"),
            networkDomains = manifest.stringList("networkDomains"),
            services = (manifest.optJSONArray("services") ?: JSONArray()).let { array ->
                (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let {
                    PluginServiceDeclaration(it.optString("name"), it.optInt("version"))
                } }
            },
        )
    }.getOrNull()

    private fun pruneVersions(pluginRoot: File, current: String) {
        pluginRoot.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") && it.name != current }
            .sortedByDescending { it.lastModified() }
            .drop(2) // current + two previous = three retained versions
            .forEach { it.deleteRecursively() }
    }

    private fun unzipSafely(zip: File, target: File) {
        val targetPath = target.canonicalPath + File.separator
        var extractedBytes = 0L
        var entries = 0
        val names = mutableSetOf<String>()
        ZipInputStream(FileInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.forEach { entry ->
                require(++entries <= 2_000) { "插件包文件数量过多" }
                require(names.add(entry.name)) { "插件包包含重复路径：${entry.name}" }
                val normalizedName = entry.name.removeSuffix("/")
                require(PluginScriptManifestValidator.validPath(normalizedName)) { "插件包包含非法路径：${entry.name}" }
                val out = File(target, entry.name)
                require(out.canonicalPath.startsWith(targetPath)) { "插件包包含非法路径" }
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            extractedBytes += count
                            require(extractedBytes <= MAX_EXTRACTED_BYTES) { "插件解压后体积过大" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                input.closeEntry()
            }
        }
    }

    private fun safePluginDir(id: String): File {
        require(ID_PATTERN.matches(id)) { "插件 ID 无效" }
        return File(root, id)
    }

    private fun requireHostApi(manifest: JSONObject) {
        val hostApi = manifest.optString("hostApi")
        require(hostApi.isBlank() || PluginHostApi.supports(hostApi)) {
            "插件要求的宿主 API $hostApi 与当前 ${PluginHostApi.VERSION} 不兼容"
        }
    }

    private fun validateInstalledManifest(manifest: JSONObject) {
        requireHostApi(manifest)
        val kind = manifest.optString("kind")
        if (kind != "player" && manifest.optString("hostApi").isNotBlank()) PluginScriptManifestValidator.validate(manifest)
    }

    private fun saveRepositories(repositories: List<PluginRepository>) {
        val array = JSONArray()
        repositories.forEach { array.put(JSONObject().put("name", it.name).put("url", it.indexUrl).put("enabled", it.enabled)) }
        prefs.edit().putString("repositories", array.toString()).apply()
    }

    private fun recoverInterruptedOperations() {
        root.listFiles().orEmpty().filter { it.name.startsWith(".staging-") }.forEach { it.deleteRecursively() }
        root.listFiles().orEmpty().filter(File::isDirectory).filterNot { it.name.startsWith(".") }.forEach { pluginRoot ->
            val currentFile = File(pluginRoot, "current")
            val current = currentFile.takeIf(File::isFile)?.readText()?.trim().orEmpty()
            if (current.isNotBlank() && File(pluginRoot, current).isDirectory) return@forEach
            val fallback = pluginRoot.listFiles().orEmpty()
                .filter { it.isDirectory && File(it, "manifest.json").isFile }
                .maxByOrNull(File::lastModified)
            if (fallback != null) runCatching { activateVersion(pluginRoot.name, fallback.name) }
        }
    }

    private fun writeAtomically(target: File, content: String) {
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        temp.writeText(content)
        runCatching {
            // rename(2) atomically replaces a file on the same filesystem and is available since API 21.
            Os.rename(temp.absolutePath, target.absolutePath)
        }.getOrElse {
            temp.delete()
            throw IllegalStateException("无法保存 ${target.name}", it)
        }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_SCRIPT_BYTES = 2L * 1024L * 1024L
        private const val MAX_EXTRACTED_BYTES = 200L * 1024L * 1024L
        private val ID_PATTERN = Regex("[a-zA-Z0-9._-]{3,100}")
        private val VERSION_PATTERN = Regex("[0-9A-Za-z][0-9A-Za-z._+-]{0,63}")
        val DEFAULT_REPOSITORY = PluginRepository(
            name = "ABU 官方插件库",
            indexUrl = "https://github.com/Mashiro000/ABU-plugins/releases/latest/download/plugins.json",
            official = true,
        )

        @Volatile private var instance: PluginManager? = null
        fun get(context: Context): PluginManager = instance ?: synchronized(this) {
            instance ?: PluginManager(context.applicationContext).also { instance = it }
        }
    }
}

/** Numeric dotted versions sort as users expect (1.10.0 after 1.9.0); legacy labels fall back to text. */
internal fun comparePluginVersions(left: String, right: String): Int {
    val a = left.split(Regex("[._+-]"))
    val b = right.split(Regex("[._+-]"))
    for (index in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrNull(index) ?: "0"
        val y = b.getOrNull(index) ?: "0"
        val numeric = x.toLongOrNull()?.let { xv -> y.toLongOrNull()?.let(xv::compareTo) }
        val compared = numeric ?: x.compareTo(y, ignoreCase = true)
        if (compared != 0) return compared
    }
    return left.compareTo(right, ignoreCase = true)
}

private fun JSONObject.stringList(name: String): List<String> {
    val values = optJSONArray(name) ?: return emptyList()
    return (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
}
