package com.limi.tvdesktop.plugins

import android.util.Base64
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec
import java.io.File
import java.security.MessageDigest
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

object PluginSignatureVerifier {
    fun verify(file: File, publicKeyBase64: String, signatureBase64: String): Boolean = runCatching {
        val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        val signatureBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
        require(publicKeyBytes.size == 32) { "Ed25519 公钥长度无效" }
        require(signatureBytes.size == 64) { "Ed25519 签名长度无效" }
        val platformResult = verifyPlatform(file, publicKeyBytes, signatureBytes)
        if (platformResult == true) return@runCatching true
        val spec = EdDSAPublicKeySpec(publicKeyBytes, EdDSANamedCurveTable.getByName("Ed25519"))
        val verifier = EdDSAEngine(MessageDigest.getInstance("SHA-512"))
        verifier.initVerify(EdDSAPublicKey(spec))
        require(file.length() <= MAX_SIGNED_FILE_BYTES) { "签名文件过大" }
        verifier.verifyOneShot(file.readBytes(), signatureBytes)
    }.getOrDefault(false)

    private fun verifyPlatform(file: File, publicKey: ByteArray, signature: ByteArray): Boolean? = runCatching {
        // RFC 8410 SubjectPublicKeyInfo prefix for a raw 32-byte Ed25519 public key.
        val prefix = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
        val key = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(prefix + publicKey))
        val verifier = Signature.getInstance("Ed25519")
        verifier.initVerify(key)
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                verifier.update(buffer, 0, count)
            }
        }
        verifier.verify(signature)
    }.getOrNull()

    fun fingerprint(publicKeyBase64: String): String {
        val bytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .take(16).joinToString(":") { "%02X".format(it) }
    }

    private const val MAX_SIGNED_FILE_BYTES = 250L * 1024L * 1024L
}
