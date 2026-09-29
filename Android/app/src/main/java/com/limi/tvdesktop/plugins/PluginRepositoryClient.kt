package com.limi.tvdesktop.plugins

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import java.net.URI

class PluginRepositoryClient internal constructor(context: Context, private val client: OkHttpClient) {
    private val cacheDir = File(context.cacheDir, "plugin-downloads").apply { mkdirs() }

    constructor(context: Context) : this(
        context,
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build(),
    )

    suspend fun fetch(repository: PluginRepository): Result<List<RemotePlugin>> = withContext(Dispatchers.IO) {
        runCatching {
            require(repository.indexUrl.startsWith("https://")) { "仓库必须使用 HTTPS" }
            client.newCall(Request.Builder().url(repository.indexUrl).build()).execute().use { response ->
                require(response.request.url.isHttps) { "仓库重定向到了非 HTTPS 地址" }
                require(response.isSuccessful) { "仓库请求失败：HTTP ${response.code}" }
                val body = requireNotNull(response.body) { "仓库响应为空" }
                require(body.contentLength() <= MAX_INDEX_BYTES || body.contentLength() < 0) { "仓库索引过大" }
                val text = body.charStream().use { it.readTextLimited(MAX_INDEX_BYTES) }
                val root = JSONObject(text)
                require(root.optInt("schemaVersion") == 1) { "不支持的仓库格式" }
                val plugins = root.optJSONArray("plugins") ?: JSONArray()
                (0 until plugins.length()).map { parsePlugin(plugins.getJSONObject(it), repository.official) }
            }
        }
    }

    suspend fun download(
        plugin: RemotePlugin,
        asset: RemotePluginAsset,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(asset.url.startsWith("https://")) { "插件下载地址必须使用 HTTPS" }
            val output = File(cacheDir, "${plugin.id}-${plugin.version}-${asset.abi}.abu-plugin")
            client.newCall(Request.Builder().url(asset.url).build()).execute().use { response ->
                require(response.request.url.isHttps) { "插件下载重定向到了非 HTTPS 地址" }
                require(response.isSuccessful) { "插件下载失败：HTTP ${response.code}" }
                val body = requireNotNull(response.body) { "插件响应为空" }
                val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes
                require(total in 0..MAX_PACKAGE_BYTES) { "插件包体积超过限制" }
                body.byteStream().use { input ->
                    output.outputStream().use { sink ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            sink.write(buffer, 0, count)
                            downloaded += count
                            require(downloaded <= MAX_PACKAGE_BYTES) { "插件包体积超过限制" }
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            output
        }.onFailure { cacheDir.listFiles()?.filter { it.name.startsWith("${plugin.id}-${plugin.version}") }?.forEach { it.delete() } }
    }

    private fun parsePlugin(json: JSONObject, officialRepository: Boolean): RemotePlugin {
        val permissionsJson = json.optJSONArray("permissions") ?: JSONArray()
        val assetsJson = json.getJSONArray("assets")
        val id = json.getString("id")
        require(id.matches(Regex("[a-zA-Z0-9._-]{3,100}"))) { "仓库包含无效插件 ID" }
        val publicKey = json.getString("publicKey")
        require(android.util.Base64.decode(publicKey, android.util.Base64.DEFAULT).size == 32) { "插件公钥无效" }
        return RemotePlugin(
            id = id,
            name = json.getString("name"),
            version = json.getString("version"),
            description = json.optString("description"),
            kind = PluginKind.valueOf(json.getString("kind").uppercase()),
            author = json.optString("author", "未知发布者"),
            publicKeyBase64 = publicKey,
            official = officialRepository,
            permissions = (0 until permissionsJson.length()).map { i ->
                val item = permissionsJson.getJSONObject(i)
                PluginPermission(item.getString("id"), item.optString("title", item.getString("id")), item.optBoolean("sensitive"))
            },
            assets = (0 until assetsJson.length()).map { i ->
                val item = assetsJson.getJSONObject(i)
                val url = item.getString("url")
                require(URI(url).let { it.scheme == "https" && !it.host.isNullOrBlank() }) { "插件资源地址必须使用 HTTPS" }
                val sha256 = item.getString("sha256")
                require(sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "插件哈希无效" }
                RemotePluginAsset(
                    abi = item.getString("abi"),
                    url = url,
                    sizeBytes = item.optLong("size"),
                    sha256 = sha256,
                    signatureBase64 = item.getString("signature"),
                )
            },
        )
    }

    private fun java.io.Reader.readTextLimited(maxChars: Int): String {
        val output = StringBuilder()
        val buffer = CharArray(8 * 1024)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.length + count <= maxChars) { "仓库索引过大" }
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    companion object {
        private const val MAX_INDEX_BYTES = 2 * 1024 * 1024
        private const val MAX_PACKAGE_BYTES = 250L * 1024L * 1024L
    }
}
