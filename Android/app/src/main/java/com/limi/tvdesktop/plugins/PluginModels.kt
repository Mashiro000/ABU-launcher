package com.limi.tvdesktop.plugins

enum class PluginKind { PLAYER, UI, DATA_SOURCE, SUBTITLE, SYSTEM }

enum class PluginTrust { OFFICIAL, VERIFIED, UNVERIFIED }

data class PluginPermission(
    val id: String,
    val title: String,
    val sensitive: Boolean = false,
)

data class PluginServiceDeclaration(val name: String, val version: Int)

data class InstalledPlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val kind: PluginKind,
    val author: String,
    val enabled: Boolean,
    val trust: PluginTrust,
    val installedBytes: Long,
    val permissions: List<PluginPermission>,
    val availableVersions: List<String> = emptyList(),
    val entry: String? = null,
    val surfaces: List<String> = emptyList(),
    val slots: List<String> = emptyList(),
    val networkDomains: List<String> = emptyList(),
    val services: List<PluginServiceDeclaration> = emptyList(),
)

data class PluginRepository(
    val name: String,
    val indexUrl: String,
    val official: Boolean = false,
    val enabled: Boolean = true,
)

data class RemotePluginAsset(
    val abi: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val signatureBase64: String,
)

data class RemotePlugin(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val kind: PluginKind,
    val author: String,
    val publicKeyBase64: String,
    val official: Boolean,
    val permissions: List<PluginPermission>,
    val assets: List<RemotePluginAsset>,
)

data class PluginVerification(
    val publicKeyBase64: String,
    val signatureBase64: String,
    val official: Boolean,
)
