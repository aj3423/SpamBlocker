package spam.blocker.util

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

        // return pair:
        //   headers: ["pattern", "flag", ...]
        //   rows: [
        //     {"pattern": "...", "flag": 3, ...}
        //     {"pattern": "...", "flag": 3, ...}
        //   ]
        fun parseToMaps(csvBytes: ByteArray): Pair<List<String>, List<Map<String, String>>> {
            val csvString = String(removeBom(csvBytes))
            val lines = csvString.lines()

            // The first line must be header
            val headers = lines.first().split(",").map { it.trim() }
            val rows = lines
                .drop(1)
                .map { it.split(",").map { it.trim() } }
                .map { headers.zip(it).toMap() }

            return Pair(headers, rows)
        }
    }
}