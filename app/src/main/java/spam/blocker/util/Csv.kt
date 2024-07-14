package spam.blocker.util

import kotlinx.serialization.json.Json

class Csv {
    companion object {

        private fun removeBom(bytes: ByteArray): ByteArray {
            val hasBOM = bytes.size >= 3 &&
                    bytes[0] == 0xEF.toByte() &&
                    bytes[1] == 0xBB.toByte() &&
                    bytes[2] == 0xBF.toByte()

            return if (hasBOM) {
                bytes.copyOfRange(3, bytes.size)
            } else {
                bytes
            }
        }

        fun parseToMaps(csvBytes: ByteArray): List<Map<String, String>> {
            val csvString = String(removeBom(csvBytes))
            val lines = csvString.lines()

            // The first line must be header
            val headers = lines.first().split(",").map { it.trim() }

            return lines
                .drop(1)
                .map { it.split(",").map { it.trim() } }
                .map { headers.zip(it).toMap() }
        }
    }
}