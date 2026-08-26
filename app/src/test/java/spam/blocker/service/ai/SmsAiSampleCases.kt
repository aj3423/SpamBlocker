package spam.blocker.service.ai

import java.io.File

data class SmsAiSample(
    val id: String,
    val want: String,
    val sms: String,
)

object SmsAiSampleCases {

    fun defaultCategories(): List<SmsAiPrompt.PromptCategory> = listOf(
        SmsAiPrompt.PromptCategory(
            "Scam",
            "fake bank, IRS, locked account, click to pay. e.g. \"Your account is suspended. Verify now: http://bit.ly/xx\"",
        ),
        SmsAiPrompt.PromptCategory(
            "Ad",
            "sale, coupon, survey, or campaign. e.g. \"50% off this weekend only. Shop: https://deals.example\"",
        ),
        SmsAiPrompt.PromptCategory(
            "Order",
            "package tracking or a booked appointment. e.g. \"Your package 8821 is out for delivery today\"",
        ),
        SmsAiPrompt.PromptCategory(
            "OTP",
            "only a passcode of digits to type in. e.g. \"123456 is your verification code\"",
        ),
        SmsAiPrompt.PromptCategory(
            "Chat",
            "a person texting you. e.g. \"On my way, 10 min\"",
        ),
    )

    fun splitSamples(text: String): List<String> {
        return text.split(Regex("\\n---\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    fun load(): List<SmsAiSample> {
        val smsList = splitSamples(loadSamplesText())
        val labels = loadLabels()
        return smsList.mapIndexed { i, sms ->
            val (id, want) = labels.getOrNull(i) ?: ("unlabeled_${i + 1}" to "")
            SmsAiSample(id = id, want = want, sms = sms)
        }
    }

    fun loadLabels(): List<Pair<String, String>> {
        val text = readResource("/sms-ai/labels.tsv")
        return text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("id\t") }
            .map { line ->
                val parts = line.split('\t')
                require(parts.size >= 2) { "bad label line: $line" }
                parts[0] to parts[1]
            }
            .toList()
    }

    fun loadSamplesText(): String {
        System.getProperty("smsAiSamples")?.let { path ->
            val f = File(path)
            if (f.isFile) return f.readText()
        }
        System.getenv("SMS_AI_SAMPLES")?.let { path ->
            val f = File(path)
            if (f.isFile) return f.readText()
        }
        listOf(
            File("build/reports/samples.txt"),
            File("app/build/reports/samples.txt"),
        ).firstOrNull { it.isFile }?.let { return it.readText() }
        return readResource("/sms-ai/samples.txt")
    }

    private fun readResource(path: String): String {
        val stream = checkNotNull(javaClass.getResourceAsStream(path)) { "missing $path" }
        return stream.bufferedReader().use { it.readText() }
    }
}
