package spam.blocker.util

import android.util.Base64
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.zip.GZIPInputStream

fun ByteArray.toHexString(): String {
    return joinToString("") { String.format("%02x", it) }
}

object Algorithm {

    fun decompressToString(compressed: ByteArray): String {
        val bis = ByteArrayInputStream(compressed)
        val gis = GZIPInputStream(bis)
        val br = BufferedReader(InputStreamReader(gis, "UTF-8"))
        val sb = StringBuilder()
        var line: String?
        while (br.readLine().also { line = it } != null) {
            sb.append(line)
        }
        br.close()
        gis.close()
        bis.close()
        return sb.toString()
    }

    fun b64Encode(raw: ByteArray): String {
        return Base64.encodeToString(raw, Base64.NO_WRAP)
    }
    fun b64Encode(raw: String): String {
        return b64Encode(raw.toByteArray())
    }
    fun b64Decode(encoded: String): ByteArray {
        return Base64.decode(encoded, Base64.NO_WRAP)
    }

    fun sha1(raw: ByteArray): ByteArray {
        val digest = MessageDigest.getInstance("SHA-1")
        val hash = digest.digest(raw)
        return hash
    }
}