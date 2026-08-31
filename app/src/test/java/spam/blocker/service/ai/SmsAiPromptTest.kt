package spam.blocker.service.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import spam.blocker.def.Def
import spam.blocker.service.ai.SmsAiPrompt.PromptCategory

class SmsAiPromptTest {

    private val categories = listOf(
        PromptCategory(
            "Scam",
            "fake bank, IRS, locked account, click to pay. e.g. \"Your account is suspended. Verify now: http://bit.ly/xx\""
        ),
        PromptCategory(
            "Ad",
            "sale, coupon, survey, or campaign. e.g. \"50% off this weekend only. Shop: https://deals.example\""
        ),
        PromptCategory(
            "Order",
            "package tracking or a booked appointment. e.g. \"Your package 8821 is out for delivery today\""
        ),
        PromptCategory("OTP", "only a passcode of digits to type in. e.g. \"123456 is your verification code\""),
        PromptCategory("Chat", "a person texting you. e.g. \"On my way, 10 min\""),
    )
    private val names = categories.map { it.name }

    @Test
    fun formatListAndQuotedSms() {
        val sms = "Reminder: dentist appointment tomorrow at 3pm\nReply C to confirm"
        val out = SmsAiPrompt.format(Def.DEFAULT_SMS_AI_PROMPT, categories, sms)
        assertEquals(
            """
            Classify the SMS into exactly one category. Use Chat only if none of the others apply.

            Categories:
            Scam: fake bank, IRS, locked account, click to pay. e.g. "Your account is suspended. Verify now: http://bit.ly/xx"
            Ad: sale, coupon, survey, or campaign. e.g. "50% off this weekend only. Shop: https://deals.example"
            Order: package tracking or a booked appointment. e.g. "Your package 8821 is out for delivery today"
            OTP: only a passcode of digits to type in. e.g. "123456 is your verification code"
            Chat: a person texting you. e.g. "On my way, 10 min"

            Pick one of: Scam, Ad, Order, OTP, Chat

            SMS:
            > Reminder: dentist appointment tomorrow at 3pm
            > Reply C to confirm

            The name of the Category that best describes this SMS is:
            """.trimIndent(),
            out,
        )
    }

    @Test
    fun formatOmitsBlankDescription() {
        assertEquals("OTP\n\nPick one of: OTP", SmsAiPrompt.formatList(listOf(PromptCategory("OTP"))))
        assertEquals(
            "OTP: digits\n\nPick one of: OTP",
            SmsAiPrompt.formatList(listOf(PromptCategory("OTP", "  digits  "))),
        )
    }

    @Test
    fun matchExactIgnoreCase() {
        assertEquals("Scam", SmsAiPrompt.matchCategory("scam", names))
        assertEquals("Chat", SmsAiPrompt.matchCategory("Chat\n", names))
        assertEquals("OTP", SmsAiPrompt.matchCategory("\"OTP\".", names))
        assertEquals("Order", SmsAiPrompt.matchCategory("- Order", names))
        assertEquals("Ad", SmsAiPrompt.matchCategory("Category: Ad", names))
    }

    @Test
    fun matchPrefersLongerName() {
        val both = listOf("Token", "Login Token")
        assertEquals("Login Token", SmsAiPrompt.matchCategory("Login Token", both))
        assertEquals("Token", SmsAiPrompt.matchCategory("Token", both))
    }

    @Test
    fun unmatchedReturnsNull() {
        assertNull(SmsAiPrompt.matchCategory("hello", names))
        assertNull(SmsAiPrompt.matchCategory("", names))
        assertNull(SmsAiPrompt.matchCategory(null, names))
    }

    @Test
    fun shouldStopWhenFirstLineIsCategory() {
        assertEquals(false, SmsAiPrompt.shouldStopGeneration("", names))
        assertEquals(false, SmsAiPrompt.shouldStopGeneration("Ch", names))
        assertEquals(true, SmsAiPrompt.shouldStopGeneration("Chat", names))
        assertEquals(true, SmsAiPrompt.shouldStopGeneration("OTP\nmore rambling", names))
        assertEquals(true, SmsAiPrompt.shouldStopGeneration("not a category\n", names))
        assertEquals(
            true,
            SmsAiPrompt.shouldStopGeneration("x".repeat(48), names),
        )
    }

    @Test
    fun categoryConstraintRegexPrefersLongerNames() {
        assertEquals(
            " ?(Login Token|Token|Ad)",
            SmsAiPrompt.categoryConstraintRegex(listOf("Ad", "Token", "Login Token")),
        )
        assertEquals(null, SmsAiPrompt.categoryConstraintRegex(listOf("  ", "")))
    }

    @Test
    fun categoryConstraintGbnfMatchesRegexShape() {
        assertEquals(
            "root ::= \" \"? (\"Login Token\" | \"Token\" | \"Ad\")",
            SmsAiPrompt.categoryConstraintGbnf(listOf("Ad", "Token", "Login Token")),
        )
        assertEquals(null, SmsAiPrompt.categoryConstraintGbnf(listOf("  ", "")))
        assertEquals(
            "root ::= \" \"? (\"Say \\\"hi\\\"\")",
            SmsAiPrompt.categoryConstraintGbnf(listOf("Say \"hi\"")),
        )
    }
}
