package spam.blocker.util

class Csv(
    //   headers: ["pattern", "flag", ...]
    val headers: List<String>,

    //   rows: [
    //     {"pattern": "...", "flag": 3, ...}
    //     {"pattern": "...", "flag": 3, ...}
    //   ]
    val rows: List<Map<String, String>>
) {
    var filename: String? = null

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

        fun parse(
            csvBytes: ByteArray,
            columnMap: Map<String, String> = mapOf()
        ): Csv {
            val csvString = String(removeBom(csvBytes))
            val lines = csvString.lines()

            // The first line must be header
            val headers = lines.first().split(",").map { it.trim() }

            // map header columns with columnMap
            // for example, map column "Spam Number" to "pattern"
            val mappedHeaders = headers.map { columnMap[it] ?: it }

            val rows = lines
                .drop(1)
                .map { it.split(",").map { it.trim() } }
                .map { mappedHeaders.zip(it).toMap() }

            return Csv(mappedHeaders, rows)
        }
    }
}