package spam.blocker.service.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URL

class SmsAiDownloadTest {

    @Test
    fun huggingFaceHostExactAndSubdomain() {
        assertTrue(SmsAiDownload.isHuggingFaceHost("huggingface.co"))
        assertTrue(SmsAiDownload.isHuggingFaceHost("cdn-lfs.huggingface.co"))
        assertTrue(SmsAiDownload.isHuggingFaceHost("HuggingFace.co"))
        assertFalse(SmsAiDownload.isHuggingFaceHost("evilhuggingface.co"))
        assertFalse(SmsAiDownload.isHuggingFaceHost("huggingface.co.evil.example"))
        assertFalse(SmsAiDownload.isHuggingFaceHost(null))
        assertFalse(SmsAiDownload.isHuggingFaceHost(""))
    }

    @Test
    fun tokenOnlyOnHttpsHuggingFace() {
        val token = "hf_secret"
        assertTrue(
            SmsAiDownload.shouldSendHfToken(
                URL("https://huggingface.co/path"),
                token,
            )
        )
        assertTrue(
            SmsAiDownload.shouldSendHfToken(
                URL("https://cdn-lfs.huggingface.co/file"),
                token,
            )
        )
        assertFalse(
            SmsAiDownload.shouldSendHfToken(
                URL("http://huggingface.co/path"),
                token,
            )
        )
        assertFalse(
            SmsAiDownload.shouldSendHfToken(
                URL("https://evilhuggingface.co/path"),
                token,
            )
        )
        assertFalse(
            SmsAiDownload.shouldSendHfToken(
                URL("https://huggingface.co/path"),
                "",
            )
        )
    }

    @Test
    fun sizeErrorRequiresCompleteDownload() {
        assertNull(SmsAiDownload.sizeError(10_000_000L, 10_000_000L, 10_000_000L))
        assertEquals(
            "incomplete download (9 / 10)",
            SmsAiDownload.sizeError(9L, 10L, 10L),
        )
        assertEquals(
            "unexpected size (9, expected 10)",
            SmsAiDownload.sizeError(9L, -1L, 10L),
        )
        assertEquals(
            "download too small (100 bytes)",
            SmsAiDownload.sizeError(100L, -1L, -1L),
        )
        assertNull(SmsAiDownload.sizeError(10_000_000L, 10_000_000L, 99L))
    }

    @Test
    fun httpsCheck() {
        assertTrue(SmsAiDownload.isHttps(URL("https://huggingface.co/x")))
        assertFalse(SmsAiDownload.isHttps(URL("http://huggingface.co/x")))
    }
}
