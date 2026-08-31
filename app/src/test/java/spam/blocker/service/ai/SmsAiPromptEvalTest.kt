package spam.blocker.service.ai

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty
import spam.blocker.def.Def
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@EnabledIfSystemProperty(named = "smsAiEval", matches = "true")
class SmsAiPromptEvalTest {

    @Test
    fun classifiesSamples() {
        val model = SmsAiLlamaServer.findModel()
        assumeTrue(
            model != null,
            "missing local model; run: mise run download-sms-ai-model",
        )
        val categories = SmsAiSampleCases.defaultCategories()
        val names = categories.map { it.name }
        val cases = SmsAiSampleCases.load()
        assumeTrue(cases.isNotEmpty(), "no SMS samples")

        SmsAiLlamaServer(model!!).ensureRunning().use { server ->
            val header = String.format("%-4s %-22s %-8s %-8s %s", "ok", "case", "want", "got", "sms")
            val lines = mutableListOf(header, "-".repeat(94))
            val failed = mutableListOf<String>()
            val workers = System.getProperty("smsAiParallel")?.toIntOrNull()
                ?: System.getenv("SMS_AI_PARALLEL")?.toIntOrNull()
                ?: 1
            val pool = Executors.newFixedThreadPool(workers.coerceAtLeast(1))
            val replies = try {
                cases.map { sample ->
                    pool.submit<String> {
                        val prompt = SmsAiPrompt.format(Def.DEFAULT_SMS_AI_PROMPT, categories, sample.sms)
                        val grammar = SmsAiPrompt.categoryConstraintGbnf(names)
                        server.complete(prompt, grammar)
                    }
                }.map { it.get() }
            } finally {
                pool.shutdown()
                pool.awaitTermination(5, TimeUnit.MINUTES)
            }
            for ((sample, raw) in cases.zip(replies)) {
                val got = SmsAiPrompt.matchCategory(raw, names) ?: "-"
                val oneLine = sample.sms.replace('\n', ' ').trim()
                val snippet = if (oneLine.length > 48) oneLine.take(47) + "…" else oneLine
                val labeled = sample.want.isNotBlank()
                val pass = !labeled || got == sample.want
                if (!pass) {
                    failed += sample.id
                }
                val mark = when {
                    !labeled -> "skip"
                    pass -> "ok"
                    else -> "FAIL"
                }
                lines += String.format(
                    "%-4s %-22s %-8s %-8s %s",
                    mark,
                    sample.id,
                    sample.want.ifBlank { "-" },
                    got,
                    snippet,
                )
                if (!pass) {
                    lines += "      raw: ${raw.trim().replace("\n", " / ")}"
                }
            }
            lines += "-".repeat(94)
            val labeledCount = cases.count { it.want.isNotBlank() }
            lines += "${labeledCount - failed.size} pass, ${failed.size} fail, ${cases.size} cases"
            val table = lines.joinToString("\n")
            println(table)
            val reportDir = File("build/reports").apply { mkdirs() }
            File(reportDir, "sms-ai-prompt-eval.txt").writeText(table + "\n")
            if (failed.isNotEmpty()) {
                throw AssertionError("failed: ${failed.joinToString()}\n$table")
            }
        }
    }
}
