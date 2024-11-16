package spam.blocker.util

class Csv(
    //   headers: ["pattern", "flags", ...]
    val headers: List<String>,

    //   rows: [
    //     ["111", "1", ...]
    //     ["222", "2", ...]
    //   ]
    val rows: List<List<String>>
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

        private fun detectSeparator(headerLine: String): String? {
            val match = "([,;|])".toRegex().find(headerLine)
            return match?.groups?.first()?.value
        }

        fun parse(
            csvBytes: ByteArray,
            columnMap: Map<String, String> = mapOf(),
        ): Csv {
            val csvString = String(removeBom(csvBytes)).trim()
            val lines = csvString.lines()

            val headerLine = lines.first()

            val sep = detectSeparator(headerLine) ?: ","

            // The first line must be header
            val headers = headerLine.split(sep).map { it.trim() }

            // map header columns with columnMap
            // e.g.:
            //   convert "Spam Number" to "pattern"
            // result:
            //   [pattern,priority,dummy]
            val mappedHeaders = headers.map { columnMap[it] ?: it }

            val rows = lines
                // drop header line
                .drop(1)

                // split the line with "," and trim spaces for each value
                // e.g., result:
                //   [123,4,5]
                .map { it.split(sep).map { it.trim() } }

            return Csv(mappedHeaders, rows)
        }
    }
}