package spam.blocker.service.bot.stream

import spam.blocker.db.RegexRule
import java.io.Closeable

private const val BATCH_SIZE = 100_000

interface RuleStream : Closeable {
    fun batchReadRules(batchSize: Int = BATCH_SIZE): List<RegexRule>
}

class ListRuleStream(rules: List<RegexRule>) : RuleStream {
    private val iterator = rules.iterator()

    override fun batchReadRules(batchSize: Int): List<RegexRule> = buildList(batchSize) {
        repeat(batchSize) {
            if (!iterator.hasNext()) return@buildList
            add(iterator.next())
        }
    }

    override fun close() = Unit
}

class GroupedRuleStream(
    val groups: Map<Int, List<RegexRule>>,
) : RuleStream {
    private val stream = ListRuleStream(groups.values.flatten())

    override fun batchReadRules(batchSize: Int) = stream.batchReadRules(batchSize)
    override fun close() = stream.close()
}

class MappingRuleStream(
    private val source: RuleStream,
    private val transform: (RegexRule) -> RegexRule,
) : RuleStream {
    override fun batchReadRules(batchSize: Int) = source.batchReadRules(batchSize).map(transform)
    override fun close() = source.close()
}

fun RuleStream.readAll(batchSize: Int = BATCH_SIZE): List<RegexRule> = use { stream ->
    buildList {
        while (true) {
            val batch = stream.batchReadRules(batchSize)
            if (batch.isEmpty()) return@buildList
            addAll(batch)
        }
    }
}
