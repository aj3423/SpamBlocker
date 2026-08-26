package spam.blocker.service.ai

import java.net.URL

object SmsAiDownload {
    const val MIN_BYTES = 1_000_000L

    fun isHttps(url: URL): Boolean {
        return url.protocol.equals("https", ignoreCase = true)
    }

    fun isHuggingFaceHost(host: String?): Boolean {
        if (host.isNullOrBlank()) {
            return false
        }
        val h = host.lowercase()
        return h == "huggingface.co" || h.endsWith(".huggingface.co")
    }

    fun shouldSendHfToken(url: URL, token: String): Boolean {
        return token.isNotEmpty() && isHttps(url) && isHuggingFaceHost(url.host)
    }

    // Null when the downloaded size is acceptable.
    fun sizeError(received: Long, contentLength: Long, expectedSize: Long): String? {
        if (contentLength > 0 && received != contentLength) {
            return "incomplete download ($received / $contentLength)"
        }
        if (contentLength <= 0 && expectedSize > 0 && received != expectedSize) {
            return "unexpected size ($received, expected $expectedSize)"
        }
        if (received < MIN_BYTES) {
            return "download too small ($received bytes)"
        }
        return null
    }
}
