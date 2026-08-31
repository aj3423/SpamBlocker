package spam.blocker.service.ai

import spam.blocker.def.Def

data class SmsAiModel(
    val id: String,
    val label: String,
    val url: String,
    val sizeBytes: Long,
    val contextWindow: Int,
    val licenseUrl: String,
    val gated: Boolean = false,
    // Hugging Face still marks 270M GPU as WIP; bad GPU logits collapse to Chat.
    val gpu: Boolean = true,
)

object SmsAiModels {
    private const val GEMMA3_1B = "https://huggingface.co/litert-community/Gemma3-1B-IT"
    private const val GEMMA3_270M = "https://huggingface.co/litert-community/gemma-3-270m-it"
    private const val QWEN25_15B = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct"

    val all: List<SmsAiModel> = listOf(
        SmsAiModel(
            id = "gemma3-270m-it-q8",
            label = "Gemma-3 270M INT8 (290MB)",
            url = "$GEMMA3_270M/resolve/main/gemma3-270m-it-q8.litertlm?download=true",
            sizeBytes = 304_005_120L,
            contextWindow = 1024,
            licenseUrl = GEMMA3_270M,
            gated = true,
            gpu = false,
        ),
        SmsAiModel(
            id = Def.DEFAULT_SMS_AI_MODEL_ID,
            label = "Gemma-3 1B INT4, 2k (557MB)",
            url = "$GEMMA3_1B/resolve/main/gemma3-1b-it-int4.litertlm?download=true",
            sizeBytes = 584_417_280L,
            contextWindow = 2048,
            licenseUrl = GEMMA3_1B,
            gated = true,
        ),
        SmsAiModel(
            id = "qwen25-1.5b-q8",
            label = "Qwen2.5 1.5B INT8, 4k (1.49GB)",
            url = "$QWEN25_15B/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
            sizeBytes = 1_597_931_520L,
            contextWindow = 4096,
            licenseUrl = QWEN25_15B,
        ),
    )

    val default: SmsAiModel = byId(Def.DEFAULT_SMS_AI_MODEL_ID)

    fun byId(id: String): SmsAiModel {
        return all.firstOrNull { it.id == id } ?: all.first { it.id == Def.DEFAULT_SMS_AI_MODEL_ID }
    }
}
