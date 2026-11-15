package spam.blocker.util

import java.io.PushbackReader

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
}

enum class CSVState { ReadingField, ReadingQuotedField, End }

/** Stateful CSV reader. It doesn't parse the whole file, rows are read on demand to avoid OOM */
class CSVParser(
    private val reader: PushbackReader,
    private val columnMap: Map<String, String> = mapOf(),
) {
    private var initialized = false
    private var finished = false
    private var separator = ','
    private var currentState = CSVState.ReadingField
    private var currentField = StringBuilder()
    private var currentRecord = mutableListOf<String>()
    lateinit var headers: List<String>
        private set

    private fun skipBOM() {
        val first = reader.read()
        if (first != -1 && first.toChar() != '\uFEFF') reader.unread(first)
    }

    private fun readOneLine(): String {
        val result = StringBuilder()
        var ch: Int
        while (reader.read().also { ch = it } != -1) {
            when (ch.toChar()) {
                '\n' -> return result.toString()
                '\r' -> Unit
                else -> result.append(ch.toChar())
            }
        }
        return result.toString()
    }

    private fun initialize() {
        if (initialized) return
        initialized = true
        skipBOM()
        val headerLine = readOneLine()
        separator = headerLine.firstOrNull { it == ',' || it == ';' || it == '|' } ?: ','
        headers = headerLine.split(separator).map { it.trim() }.map { columnMap[it] ?: it }
    }

    fun readHeaders(): List<String> {
        initialize()
        return headers
    }

    /** Returns up to [maxRows] rows, or an empty list at end of input. */
    fun readRows(maxRows: Int): List<List<String>> {
        require(maxRows > 0)
        initialize()
        if (finished) return emptyList()

        val rows = ArrayList<List<String>>(maxRows)
        fun finishRow() {
            currentRecord.add(currentField.toString())
            currentField = StringBuilder()
            rows.add(currentRecord)
            currentRecord = mutableListOf()
        }

        var c = -1
        while (rows.size < maxRows && reader.read().also { c = it } != -1) {
            when (currentState) {
                CSVState.ReadingField -> when (c.toChar()) {
                    separator -> {
                        currentRecord.add(currentField.toString())
                        currentField = StringBuilder()
                    }
                    '"' -> currentState = CSVState.ReadingQuotedField
                    '\r' -> Unit
                    '\n' -> finishRow()
                    else -> currentField.append(c.toChar())
                }
                CSVState.ReadingQuotedField -> when (c.toChar()) {
                    '"' -> currentState = CSVState.ReadingField
                    else -> currentField.append(c.toChar())
                }
                else -> error("Unexpected CSV parser state")
            }
        }

        if (c == -1) {
            finished = true
            if (currentField.isNotEmpty() || currentRecord.isNotEmpty()) finishRow()
        }
        return rows
    }

    fun parse(): Csv {
        initialize()
        val rows = mutableListOf<List<String>>()
        while (true) {
            val batch = readRows(1_000)
            if (batch.isEmpty()) break
            rows.addAll(batch)
        }
        return Csv(headers, rows)
    }
}
