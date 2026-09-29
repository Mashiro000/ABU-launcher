package com.limi.tvdesktop.player

/** Distinguishes decoder stalls from slow networks before requesting an engine fallback. */
class PlaybackHealthMonitor(private val startedAtMs: Long = now()) {
    enum class Decision { HEALTHY, WAITING_FOR_NETWORK, RETRY_MEDIA3, SWITCH_ENGINE }

    private var firstFrameAtMs: Long? = null
    private var lastPositionMs = 0L
    private var lastProgressAtMs = startedAtMs
    private var bufferingSinceMs: Long? = startedAtMs
    private var retryIssued = false

    fun onFirstFrame(atMs: Long = now()) {
        firstFrameAtMs = atMs
        lastProgressAtMs = atMs
    }

    fun sample(
        positionMs: Long,
        bufferedPositionMs: Long,
        isBuffering: Boolean,
        hasVideo: Boolean,
        atMs: Long = now(),
    ): Decision {
        if (positionMs > lastPositionMs + 250L) {
            lastPositionMs = positionMs
            lastProgressAtMs = atMs
        }
        if (isBuffering) {
            if (bufferingSinceMs == null) bufferingSinceMs = atMs
        } else bufferingSinceMs = null

        val bufferedAhead = (bufferedPositionMs - positionMs).coerceAtLeast(0L)
        val noFrameWithData = hasVideo && firstFrameAtMs == null && bufferedAhead >= 3_000L && atMs - startedAtMs >= 8_000L
        val stalledWithData = firstFrameAtMs != null && !isBuffering && bufferedAhead >= 4_000L && atMs - lastProgressAtMs >= 12_000L
        if (noFrameWithData || stalledWithData) return Decision.SWITCH_ENGINE

        val bufferingFor = bufferingSinceMs?.let { atMs - it } ?: 0L
        if (bufferingFor >= 20_000L) {
            if (bufferedAhead < 1_000L) return Decision.WAITING_FOR_NETWORK
            if (!retryIssued) {
                retryIssued = true
                return Decision.RETRY_MEDIA3
            }
            if (bufferingFor >= 30_000L) return Decision.SWITCH_ENGINE
        }
        return Decision.HEALTHY
    }

    fun reset(atMs: Long = now()) {
        firstFrameAtMs = null
        lastPositionMs = 0L
        lastProgressAtMs = atMs
        bufferingSinceMs = atMs
        retryIssued = false
    }

    companion object {
        private fun now() = android.os.SystemClock.elapsedRealtime()
    }
}
