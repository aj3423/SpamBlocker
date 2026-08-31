package spam.blocker.service.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import spam.blocker.def.Def
import java.io.File

class SmsAiPromptSamplesTest {

    @Test
    fun loadsScrubbedSamplesAndLabels() {
        val cases = SmsAiSampleCases.load()
        val labels = SmsAiSampleCases.loadLabels()
        assertEquals(50, cases.size)
        assertEquals(labels.map { it.first }, cases.map { it.id })
        assertEquals(labels.map { it.second }, cases.map { it.want })
        assertEquals(
            mapOf("Scam" to 10, "Ad" to 10, "Order" to 10, "OTP" to 10, "Chat" to 10),
            cases.groupingBy { it.want }.eachCount(),
        )
        assertTrue(cases.all { it.sms.isNotBlank() })
        assertTrue(cases.none { "\n---\n" in it.sms })
        assertTrue(cases.map { it.sms }.toSet().size == cases.size)
    }

    @Test
    fun formatsDefaultPromptAroundEachSample() {
        val categories = SmsAiSampleCases.defaultCategories()
        val cases = SmsAiSampleCases.load()
        cases.forEach { sample ->
            val out = SmsAiPrompt.format(Def.DEFAULT_SMS_AI_PROMPT, categories, sample.sms)
            assertTrue(out.startsWith("Classify the SMS into exactly one category. Use Chat only if none of the others apply."))
            assertTrue(out.contains("Pick one of: Scam, Ad, Order, OTP, Chat"))
            sample.sms.lineSequence().forEach { line ->
                assertTrue(out.contains("> $line"), "quoted line missing for ${sample.id}: $line")
            }
            assertTrue(out.endsWith("The name of the Category that best describes this SMS is:"))
        }
    }

    @Test
    fun reportsDumpParsesIfPresent() {
        val dump = listOf(
            File("build/reports/samples.txt"),
            File("app/build/reports/samples.txt"),
        ).firstOrNull { it.isFile } ?: return
        val fromDump = SmsAiSampleCases.splitSamples(dump.readText())
        val fromResource = SmsAiSampleCases.splitSamples(
            checkNotNull(javaClass.getResourceAsStream("/sms-ai/samples.txt"))
                .bufferedReader().readText(),
        )
        assertEquals(fromResource.size, fromDump.size)
        assertEquals(fromResource, fromDump)
    }
}
