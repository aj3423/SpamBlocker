package spam.blocker.util

import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object BackupUtils {
    private const val HEADER_TAG = "Encryption:AES-GCM"
    private const val KEY_LENGTH_BITS = 256
    private const val PBKDF2_ITERATIONS = 600_000

    // Fixed salt for deriving key hash stored in SharedPref
    private val STORAGE_SALT = "BackupKeySalt".toByteArray()

    private fun deriveKeyBytes(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }


    /**
     * Derive key hash (using PBKDF2-HMAC-SHA256 600,000 iterations) to save in SharedPref.
     */
    fun hashKey(passphrase: String): String {
        val bytes = deriveKeyBytes(passphrase.toCharArray(), STORAGE_SALT)
        return Algorithm.b64Encode(bytes)
    }

    /**
     * Check if file content starts with "Encryption:AES-GCM" header tag.
     */
    fun isEncrypted(data: ByteArray): Boolean {
        val headerBytes = HEADER_TAG.toByteArray()
        if (data.size < headerBytes.size) return false
        for (i in headerBytes.indices) {
            if (data[i] != headerBytes[i]) return false
        }
        return true
    }

    /**
     * Encrypt data using AES-GCM 256-bit with the provided 32-byte key.
     * Output format: [HEADER_TAG bytes] + [12 bytes IV + Ciphertext + Auth Tag]
     */
    fun encryptBackup(data: ByteArray, keyBytes: ByteArray): ByteArray {
        val headerBytes = HEADER_TAG.toByteArray()
        val encryptedWithIv = Algorithm.aesGcmEnc(data, keyBytes)
        return headerBytes + encryptedWithIv
    }

    /**
     * Decrypt encrypted backup data using provided passphrase string.
     * Input format expected: [HEADER_TAG bytes] + [12 bytes IV + Ciphertext + Auth Tag]
     */
    fun decryptBackup(encryptedData: ByteArray, passphrase: String): ByteArray {
        return decryptBackup(encryptedData, deriveKeyBytes(passphrase.toCharArray(), STORAGE_SALT))
    }

    fun decryptBackup(encryptedData: ByteArray, keyBytes: ByteArray): ByteArray {
        val headerBytes = HEADER_TAG.toByteArray()
        require(isEncrypted(encryptedData)) { "Data is not encrypted with expected header" }
        return Algorithm.aesGcmDec(
            encryptedData.copyOfRange(headerBytes.size, encryptedData.size),
            keyBytes,
        )
    }
}
