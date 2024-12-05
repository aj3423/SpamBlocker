package spam.blocker.util

import android.util.Base64
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object Algorithm {

    fun compressString(data: String): ByteArray {
        val bos = ByteArrayOutputStream(data.length)
        val gzip = GZIPOutputStream(bos)
        gzip.write(data.toByteArray())
        gzip.close()
        return bos.toByteArray()
    }

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
}