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

enum class CSVState {
    ReadingField,
    ReadingQuotedField,
    ReadingNewline,
    ReadingComma,
    End
}

// A simple state machine for parsing csv byte by byte.
class CSVParser(
    private val reader: PushbackReader,
    private val columnMap: Map<String, String> = mapOf(),
) {
    private var currentState = CSVState.ReadingField
    private var currentField = StringBuilder()
    private val records = mutableListOf<List<String>>()
    private var currentRecord = mutableListOf<String>()

    // Check for UTF-8 BOM (EF BB BF)
    private fun skipBOM() {
        val firstChar = reader.read()
        if (firstChar.toChar() == '\uFEFF') { // it's bom
            reader.read() // skip 2 more bytes
            reader.read()
        } else { //
            reader.unread(firstChar)
        }
    }

    private fun readOneLine(): String {
        val sb = StringBuilder()

        var ch: Int
        while (reader.read().also { ch = it } != -1) {
            if (ch.toChar() == '\n') {
                break
            }
            sb.append(ch.toChar())
        }
        return sb.toString()
    }

    // The delimiter can be either of: , ; |
    private fun detectSeparator(headerLine: String) : Char {
        val delimiters = setOf(',', ';', '|')
        return headerLine.firstOrNull { it in delimiters } ?: ','
    }

    fun parse(
    ): Csv {
        skipBOM()

        val headerLine = readOneLine()

        val separator = detectSeparator(headerLine)

        var c: Int

        while (reader.read().also { c = it } != -1) {
            when (currentState) {
                CSVState.ReadingField -> when (c.toChar()) {
                    separator-> {
                        currentRecord.add(currentField.toString())
                        currentField.clear()
                        currentState = CSVState.ReadingField
                    }
                    '"' -> currentState = CSVState.ReadingQuotedField
                    '\n', '\r' -> {
                        currentRecord.add(currentField.toString())
                        currentField.clear()
                        records.add(currentRecord)
                        currentRecord = mutableListOf()
                        currentState = CSVState.ReadingField
                    }
                    else -> currentField.append(c.toChar())
                }

                CSVState.ReadingQuotedField -> when (c.toChar()) {
                    '"' -> currentState = CSVState.ReadingField
                    else -> currentField.append(c.toChar())
                }

                CSVState.ReadingNewline -> {
                    if (c.toChar() == '\n') {
                        currentState = CSVState.ReadingField
                    } else {
                        currentField.append(c.toChar())
                        currentState = CSVState.ReadingField
                    }
                }

                CSVState.ReadingComma -> {
                    currentState = CSVState.ReadingField
                }

                CSVState.End -> break
            }
        }

        // Handle the last record if it's not empty
        if (currentRecord.isNotEmpty()) {
            records.add(currentRecord)
        }

        // The first line must be header
        val headers = headerLine.split(separator).map { it.trim() }

        // map header columns with columnMap
        // e.g.:
        //   convert "Spam Number" to "pattern"
        // result:
        //   [pattern,priority,dummy]
        val mappedHeaders = headers.map { columnMap[it] ?: it }

        return Csv(mappedHeaders, records)
    }
}
