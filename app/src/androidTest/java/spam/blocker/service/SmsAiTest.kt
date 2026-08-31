package spam.blocker.service

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import junit.framework.TestCase.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import spam.blocker.db.SmsAiCategory
import spam.blocker.db.SmsAiCategoryTable
import spam.blocker.def.Def
import spam.blocker.service.ai.SmsAiClassifier
import spam.blocker.service.checker.BySmsAi
import spam.blocker.service.checker.Checker
import spam.blocker.util.spf

class SmsAiTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setup() {
        spf.SharedPref(ctx).clear()
        SmsAiCategoryTable.clearAll(ctx)
        SmsAiClassifier.override = null
    }

    @After
    fun teardown() {
        SmsAiClassifier.override = null
    }

    @Test
    fun blocksScamCategory() {
        spf.SmsAi(ctx).isEnabled = true
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(name = "Scam", blockEnabled = true, blockPriority = 0)
        )
        SmsAiClassifier.override = { _, _, _ -> Pair("Scam", null) }

        val (result, _, _) = Checker.checkSms(ctx, "12345", "Your bank account is locked, click here")
        assertEquals(Def.RESULT_BLOCKED_BY_SMS_AI, result.type)
        assertEquals("Scam", (result as BySmsAi).detail.categoryName)
    }

    @Test
    fun allowsChatCategory() {
        spf.SmsAi(ctx).isEnabled = true
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(
                name = "Chat",
                allowEnabled = true,
                allowPriority = 10,
                blockEnabled = false,
            )
        )
        SmsAiClassifier.override = { _, _, _ -> Pair("Chat", null) }

        val (result, _, _) = Checker.checkSms(ctx, "12345", "See you at 6")
        assertEquals(Def.RESULT_ALLOWED_BY_SMS_AI, result.type)
    }

    @Test
    fun cachedChatAllowBeatsLaterBlockCategories() {
        spf.SmsAi(ctx).isEnabled = true
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(
                name = "Chat",
                allowEnabled = true,
                allowPriority = 10,
                blockEnabled = false,
            )
        )
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(name = "Scam", blockEnabled = true, blockPriority = 0)
        )
        var calls = 0
        SmsAiClassifier.override = { _, _, _ ->
            calls++
            Pair("Chat", null)
        }

        val (result, _, _) = Checker.checkSms(ctx, "12345", "See you at 6")
        assertEquals(1, calls)
        assertEquals(Def.RESULT_ALLOWED_BY_SMS_AI, result.type)
        assertEquals("Chat", (result as BySmsAi).detail.categoryName)
    }

    @Test
    fun classifiesOnceThenAppliesCachedCategory() {
        spf.SmsAi(ctx).isEnabled = true
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(
                name = "Chat",
                allowEnabled = true,
                allowPriority = 10,
                blockEnabled = false,
            )
        )
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(name = "Scam", blockEnabled = true, blockPriority = 0)
        )
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(name = "Ad", blockEnabled = true, blockPriority = 0)
        )
        var calls = 0
        SmsAiClassifier.override = { _, _, _ ->
            calls++
            Pair("Scam", null)
        }

        val (result, _, _) = Checker.checkSms(ctx, "12345", "Your bank account is locked, click here")
        assertEquals(1, calls)
        assertEquals(Def.RESULT_BLOCKED_BY_SMS_AI, result.type)
        assertEquals("Scam", (result as BySmsAi).detail.categoryName)
    }

    @Test
    fun disabledSkipsClassification() {
        spf.SmsAi(ctx).isEnabled = false
        SmsAiCategoryTable.addNew(
            ctx,
            SmsAiCategory(name = "Scam", blockEnabled = true, blockPriority = 100)
        )
        SmsAiClassifier.override = { _, _, _ -> Pair("Scam", null) }

        val (result, _, _) = Checker.checkSms(ctx, "12345", "spam")
        assertEquals(Def.RESULT_ALLOWED_BY_DEFAULT, result.type)
    }
}
