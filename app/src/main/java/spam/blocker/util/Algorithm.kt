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

    fun aesGcmEnc(data: ByteArray, keyBytes: ByteArray): ByteArray {
        val iv = ByteArray(12).also { java.security.SecureRandom().nextBytes(it) }
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, iv)) // 128: trailing tag length
        val encrypted = cipher.doFinal(data)
        return iv + encrypted
    }

    fun aesGcmDec(ciphertextWithIv: ByteArray, keyBytes: ByteArray): ByteArray {
        val iv = ciphertextWithIv.copyOfRange(0, 12)
        val ciphertext = ciphertextWithIv.copyOfRange(12, ciphertextWithIv.size)
        val keySpec = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }
}