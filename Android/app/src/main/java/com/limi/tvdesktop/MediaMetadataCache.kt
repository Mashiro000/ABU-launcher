package com.limi.tvdesktop

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Persistent cache for the expensive secondary requests made by a media detail page. */
internal object MediaMetadataCache {
    private const val MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private var directory: File? = null
    private val details = ConcurrentHashMap<String, MediaDetailInfo>()
    private val episodes = ConcurrentHashMap<String, List<EpisodeInfo>>()
    private val similar = ConcurrentHashMap<String, List<MediaItemInfo>>()

    fun init(context: Context) {
        if (directory == null) directory = File(context.filesDir, "media_metadata_cache").apply { mkdirs() }
    }

    suspend fun detail(key: String): MediaDetailInfo? = details[key] ?: read(key, "detail") { detailFromJson(it) }?.also { details[key] = it }
    suspend fun episodes(key: String): List<EpisodeInfo>? = episodes[key] ?: read(key, "episodes") { root ->
        root.optJSONArray("items")?.let { array -> List(array.length()) { episodeFromJson(array.getJSONObject(it)) } }
    }?.also { episodes[key] = it }
    suspend fun similar(key: String): List<MediaItemInfo>? = similar[key] ?: read(key, "similar") { root ->
        root.optJSONArray("items")?.let { array -> List(array.length()) { MediaItemInfo.fromJson(array.getJSONObject(it)) } }
    }?.also { similar[key] = it }

    suspend fun putDetail(key: String, value: MediaDetailInfo) { details[key] = value; write(key, "detail", detailToJson(value)) }
    suspend fun putEpisodes(key: String, value: List<EpisodeInfo>) { episodes[key] = value; write(key, "episodes", JSONObject().put("items", JSONArray().apply { value.forEach { put(episodeToJson(it)) } })) }
    suspend fun putSimilar(key: String, value: List<MediaItemInfo>) { similar[key] = value; write(key, "similar", JSONObject().put("items", JSONArray().apply { value.forEach { put(it.toJson()) } })) }

    private fun file(key: String, kind: String): File? {
        val digest = MessageDigest.getInstance("SHA-256").digest("$kind:$key".toByteArray()).joinToString("") { "%02x".format(it) }
        return directory?.let { File(it, "$digest.json") }
    }

    private suspend fun <T> read(key: String, kind: String, decode: (JSONObject) -> T?): T? = withContext(Dispatchers.IO) {
        val source = file(key, kind) ?: return@withContext null
        if (!source.exists() || source.length() == 0L || System.currentTimeMillis() - source.lastModified() > MAX_AGE_MS) return@withContext null
        runCatching { decode(JSONObject(source.readText())) }.getOrNull()
    }

    private suspend fun write(key: String, kind: String, value: JSONObject) = withContext(Dispatchers.IO) {
        runCatching {
            val target = file(key, kind) ?: return@runCatching
            val temp = File(target.parentFile, target.name + ".tmp")
            value.put("cachedAt", System.currentTimeMillis())
            temp.writeText(value.toString())
            if (!temp.renameTo(target)) { target.writeText(value.toString()); temp.delete() }
        }
    }

    private fun episodeToJson(value: EpisodeInfo) = JSONObject().apply {
        put("id", value.id); put("title", value.title); put("season", value.seasonNumber); put("episode", value.episodeNumber)
        put("durationText", value.durationText); put("overview", value.overview); put("thumbUrl", value.thumbUrl); put("streamUrl", value.streamUrl)
        put("position", value.playbackPositionMs); put("duration", value.durationMs)
        value.mediaSource?.let { put("source", sourceToJson(it)) }
    }

    private fun episodeFromJson(json: JSONObject) = EpisodeInfo(
        id=json.optString("id"), title=json.optString("title"), seasonNumber=json.optInt("season",1), episodeNumber=json.optInt("episode",1),
        durationText=json.optString("durationText"), overview=json.optString("overview"), thumbUrl=json.optString("thumbUrl"), streamUrl=json.optString("streamUrl"),
        playbackPositionMs=json.optLong("position"), durationMs=json.optLong("duration"), mediaSource=json.optJSONObject("source")?.let(::sourceFromJson)
    )

    private fun detailToJson(value: MediaDetailInfo) = JSONObject().apply {
        put("originalTitle",value.originalTitle);put("overview",value.overview);put("premiereDate",value.premiereDate);put("officialRating",value.officialRating)
        put("communityRating",value.communityRating);put("productionYear",value.productionYear);put("runtimeMs",value.runtimeMs);put("status",value.status);put("count",value.recursiveItemCount)
        put("genres",JSONArray(value.genres));put("studios",JSONArray(value.studios));put("countries",JSONArray(value.countries))
        put("people",JSONArray().apply { value.people.forEach { p -> put(JSONObject().put("id",p.id).put("name",p.name).put("role",p.role).put("type",p.type).put("imageUrl",p.imageUrl)) } })
        value.mediaSource?.let { put("source",sourceToJson(it)) }
    }

    private fun detailFromJson(json: JSONObject) = MediaDetailInfo(
        originalTitle=json.optString("originalTitle"),overview=json.optString("overview"),premiereDate=json.optString("premiereDate"),officialRating=json.optString("officialRating"),
        communityRating=json.optString("communityRating"),productionYear=json.optString("productionYear"),runtimeMs=json.optLong("runtimeMs"),genres=json.stringList("genres"),studios=json.stringList("studios"),countries=json.stringList("countries"),status=json.optString("status"),recursiveItemCount=json.optInt("count"),
        people=json.optJSONArray("people")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { PersonInfo(it.optString("id"),it.optString("name"),it.optString("role"),it.optString("type"),it.optString("imageUrl")) } } }.orEmpty(),
        mediaSource=json.optJSONObject("source")?.let(::sourceFromJson)
    )

    private fun sourceToJson(value: MediaSourceInfo)=JSONObject().apply {
        put("container",value.container);put("name",value.name);put("size",value.sizeBytes);put("runtime",value.runTimeMs)
        value.video?.let { put("video",streamToJson(it)) };put("audios",JSONArray().apply { value.audios.forEach { put(streamToJson(it)) } });put("subtitles",JSONArray().apply { value.subtitles.forEach { put(streamToJson(it)) } })
    }
    private fun sourceFromJson(json:JSONObject)=MediaSourceInfo(json.optString("container"),json.optString("name"),json.optLong("size"),json.optLong("runtime"),json.optJSONObject("video")?.let(::streamFromJson),json.streamList("audios"),json.streamList("subtitles"))
    private fun streamToJson(value:MediaStreamInfo)=JSONObject().put("type",value.type).put("title",value.displayTitle).put("codec",value.codec).put("width",value.width).put("height",value.height).put("channels",value.channels).put("language",value.language).put("index",value.index).put("external",value.isExternal).put("url",value.deliveryUrl)
    private fun streamFromJson(json:JSONObject)=MediaStreamInfo(json.optString("type"),json.optString("title"),json.optString("codec"),json.optInt("width"),json.optInt("height"),json.optInt("channels"),json.optString("language"),json.optInt("index",-1),json.optBoolean("external"),json.optString("url"))
    private fun JSONObject.stringList(name:String)=optJSONArray(name)?.let { a -> List(a.length()) { a.optString(it) } }.orEmpty()
    private fun JSONObject.streamList(name:String)=optJSONArray(name)?.let { a -> List(a.length()) { streamFromJson(a.getJSONObject(it)) } }.orEmpty()
}
