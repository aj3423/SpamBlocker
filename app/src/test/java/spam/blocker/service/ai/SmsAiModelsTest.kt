package spam.blocker.service.ai

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import spam.blocker.def.Def

class SmsAiModelsTest {

    @Test
    fun defaultIsGemma3Int4() {
        assertEquals(Def.DEFAULT_SMS_AI_MODEL_ID, SmsAiModels.default.id)
        assertEquals(Def.DEFAULT_SMS_AI_MODEL_ID, SmsAiModels.byId(Def.DEFAULT_SMS_AI_MODEL_ID).id)
    }

    @Test
    fun byIdSelectsKnownModels() {
        SmsAiModels.all.forEach { model ->
            assertEquals(model, SmsAiModels.byId(model.id))
            assertTrue(model.sizeBytes < 2L * 1024 * 1024 * 1024)
            assertTrue(model.url.startsWith("https://huggingface.co/"))
            assertTrue(model.url.contains(".litertlm"))
        }
    }

    @Test
    fun byIdFallsBackToDefault() {
        assertEquals(SmsAiModels.default, SmsAiModels.byId("unknown-model"))
        assertEquals(SmsAiModels.default, SmsAiModels.byId(""))
    }

    @Test
    fun gemmaIsGatedQwenIsNot() {
        assertTrue(SmsAiModels.byId("gemma3-270m-it-q8").gated)
        assertTrue(SmsAiModels.byId(Def.DEFAULT_SMS_AI_MODEL_ID).gated)
        assertFalse(SmsAiModels.byId("qwen25-1.5b-q8").gated)
    }

    @Test
    fun gemma270mSkipsGpu() {
        assertFalse(SmsAiModels.byId("gemma3-270m-it-q8").gpu)
        assertTrue(SmsAiModels.default.gpu)
    }
}
