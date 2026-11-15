package spam.blocker.service.bot.stream

import android.content.Context
import spam.blocker.db.RegexRule
import spam.blocker.util.CSVParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PushbackReader

/** A single-use, closeable stream of CSV rules. */
class CsvRuleStream(
    private val input: InputStream,
    columnMap: Map<String, String>,
) : RuleStream {
    private val parser = CSVParser(
        PushbackReader(BufferedReader(InputStreamReader(input))),
        columnMap,
    )

    val headers = parser.readHeaders()

    override fun batchReadRules(batchSize: Int): List<RegexRule> {
        require(batchSize > 0)
        return parser.readRows(batchSize).map { RegexRule.fromMap(headers.zip(it).toMap()) }
    }

    override fun close() = input.close()

    companion object {
        fun fromByteStream(ctx: Context, stream: ByteStream, columnMap: Map<String, String>): CsvRuleStream {
            return CsvRuleStream(stream.openStream(ctx), columnMap)
        }
    }
}
