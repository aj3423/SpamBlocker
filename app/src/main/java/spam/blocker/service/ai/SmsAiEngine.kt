package spam.blocker.service.ai

import android.content.Context
import android.os.Build
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.ResponseFormat
import com.google.ai.edge.litertlm.SamplerConfig
import spam.blocker.BuildConfig
import spam.blocker.db.SmsAiCategoryTable
import spam.blocker.util.logi
import spam.blocker.util.spf
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object SmsAiEngine {
    private const val LOAD_TIMEOUT_MS = 30_000L
    private const val GENERATE_TIMEOUT_MS = 20_000L
    // Category names are a few tokens. Cap decode so the model cannot fill the KV cache.
    private const val MAX_OUTPUT_TOKENS = 24
    private val lock = ReentrantLock()
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "sms-ai").apply { isDaemon = true }
    }
    private val timed = Executors.newCachedThreadPool { r ->
        Thread(r, "sms-ai-timed").apply { isDaemon = true }
    }
    private val greedySampler = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0)

    @Volatile
    private var loadedModelId: String? = null
    @Volatile
    private var engine: Engine? = null

    fun isAbiSupported(): Boolean {
        return Build.SUPPORTED_ABIS.contains("arm64-v8a")
    }

    fun modelDir(ctx: Context): File {
        return File(ctx.applicationContext.filesDir, "sms_ai_models").apply { mkdirs() }
    }

    fun modelFile(ctx: Context, model: SmsAiModel): File {
        return File(modelDir(ctx), "${model.id}.litertlm")
    }

    fun isDownloaded(ctx: Context, model: SmsAiModel): Boolean {
        val f = modelFile(ctx, model)
        if (!f.exists()) {
            return false
        }
        return if (model.sizeBytes > 0) {
            f.length() == model.sizeBytes
        } else {
            f.length() >= SmsAiDownload.MIN_BYTES
        }
    }

    fun deleteModel(ctx: Context, model: SmsAiModel) {
        val app = ctx.applicationContext
        lock.withLock {
            if (loadedModelId == model.id) {
                closeLocked()
            }
        }
        modelFile(app, model).delete()
        File(modelFile(app, model).path + ".partial").delete()
    }

    // Returns error message, or null on success.
    fun download(
        ctx: Context,
        model: SmsAiModel,
        onProgress: (received: Long, total: Long) -> Unit,
    ): String? {
        val app = ctx.applicationContext
        val dest = modelFile(app, model)
        val partial = File(dest.path + ".partial")
        val token = spf.SmsAi(app).hfToken.trim()
        var conn: HttpURLConnection? = null
        return try {
            conn = openDownload(model.url, token)
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_UNAUTHORIZED || code == HttpURLConnection.HTTP_FORBIDDEN) {
                val detail = conn.getHeaderField("X-Error-Message")
                    ?: conn.errorStream?.bufferedReader()?.readText()?.trim()?.take(200)
                return gatedError(model, token.isNotEmpty(), detail)
            }
            if (code !in 200..299) {
                return "HTTP $code"
            }
            val contentLength = conn.contentLengthLong
            val total = when {
                contentLength > 0 -> contentLength
                model.sizeBytes > 0 -> model.sizeBytes
                else -> -1L
            }
            var received = 0L
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        received += n
                        onProgress(received, total)
                    }
                }
            }
            val sizeErr = SmsAiDownload.sizeError(received, contentLength, model.sizeBytes)
            if (sizeErr != null) {
                partial.delete()
                return sizeErr
            }
            if (dest.exists()) dest.delete()
            if (!partial.renameTo(dest)) {
                partial.copyTo(dest, overwrite = true)
                partial.delete()
            }
            null
        } catch (e: Exception) {
            partial.delete()
            e.message ?: e.toString()
        } finally {
            conn?.disconnect()
        }
    }

    private fun gatedError(model: SmsAiModel, hasToken: Boolean, detail: String?): String {
        val extra = if (!detail.isNullOrBlank()) " ($detail)" else ""
        return if (!model.gated && !hasToken) {
            "Hugging Face denied this download$extra"
        } else if (hasToken) {
            "Hugging Face denied this download$extra. Accept the license on the model page, and use a token with Read access."
        } else {
            "This model is gated on Hugging Face$extra. Add a Hugging Face token (Read) and accept the license, then download again."
        }
    }

    private fun openDownload(startUrl: String, token: String): HttpURLConnection {
        var current = startUrl
        repeat(8) {
            val url = URL(current)
            if (!SmsAiDownload.isHttps(url)) {
                throw IllegalStateException("refusing non-HTTPS download")
            }
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", "SpamBlocker/${BuildConfig.VERSION_NAME}")
            conn.setRequestProperty("Accept", "*/*")
            if (SmsAiDownload.shouldSendHfToken(url, token)) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            conn.connect()
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("redirect without Location")
                }
                current = URL(url, location).toString()
            } else {
                return conn
            }
        }
        throw IllegalStateException("too many redirects")
    }

    fun close() {
        lock.withLock { closeLocked() }
    }

    private fun closeLocked() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        engine = null
        loadedModelId = null
    }

    fun preload(ctx: Context) {
        val appCtx = ctx.applicationContext
        val spf = spf.SmsAi(appCtx)
        if (!spf.isEnabled || !isAbiSupported()) {
            return
        }
        val model = SmsAiModels.byId(spf.modelId)
        if (!isDownloaded(appCtx, model)) {
            return
        }
        io.execute {
            lock.withLock { ensureLoaded(appCtx, model) }
        }
    }

    private fun abandonCreate(future: Future<Engine>) {
        timed.execute {
            try {
                future.get()?.close()
            } catch (_: Throwable) {
            }
        }
    }

    private fun createEngine(app: Context, model: SmsAiModel, backend: Backend): Engine {
        val cache = File(app.cacheDir, "sms_ai_cache").apply { mkdirs() }
        val created = Engine(
            EngineConfig(
                modelPath = modelFile(app, model).absolutePath,
                backend = backend,
                maxNumTokens = model.contextWindow,
                cacheDir = cache.absolutePath,
            )
        )
        created.initialize()
        return created
    }

    private fun ensureLoaded(ctx: Context, model: SmsAiModel): String? {
        val app = ctx.applicationContext
        if (loadedModelId == model.id && engine != null) {
            return null
        }
        closeLocked()
        if (!isAbiSupported()) {
            return "unsupported ABI"
        }
        if (!isDownloaded(app, model)) {
            return "model not downloaded"
        }
        val backends = buildList {
            if (model.gpu) add(Backend.GPU())
            add(Backend.CPU())
        }
        var lastError: String? = null
        for (backend in backends) {
            val future = timed.submit<Engine> { createEngine(app, model, backend) }
            try {
                engine = future.get(LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                loadedModelId = model.id
                logi("sms-ai loaded ${model.id} on ${backend.name}")
                return null
            } catch (e: TimeoutException) {
                lastError = "timeout"
                future.cancel(true)
                abandonCreate(future)
            } catch (t: Throwable) {
                lastError = rootMessage(t)
                closeLocked()
            }
        }
        return lastError ?: "failed to load model"
    }

    private fun rootMessage(t: Throwable): String {
        val root = generateSequence(t) { it.cause }.last()
        return root.message ?: root.toString()
    }

    fun generate(
        ctx: Context,
        prompt: String,
        shouldStop: (String) -> Boolean = { false },
        responseFormat: ResponseFormat? = null,
    ): Pair<String?, String?> {
        val app = ctx.applicationContext
        val model = SmsAiModels.byId(spf.SmsAi(app).modelId)
        return lock.withLock {
            val loadErr = ensureLoaded(app, model)
            if (loadErr != null) {
                return@withLock Pair(null, loadErr)
            }
            generateLocked(prompt, shouldStop, responseFormat)
        }
    }

    private fun generateLocked(
        prompt: String,
        shouldStop: (String) -> Boolean,
        responseFormat: ResponseFormat?,
    ): Pair<String?, String?> {
        val loaded = engine ?: return Pair(null, "engine not ready")
        var conversation: Conversation? = null
        try {
            conversation = loaded.createConversation(
                ConversationConfig(
                    samplerConfig = greedySampler,
                    maxOutputToken = MAX_OUTPUT_TOKENS,
                    enableResponseFormat = responseFormat != null,
                )
            )
            val active = conversation
            val done = CompletableFuture<String>()
            val acc = StringBuilder()
            active.sendMessageAsync(
                prompt,
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        acc.append(message.toString())
                        val text = acc.toString()
                        if (!done.isDone && shouldStop(text)) {
                            done.complete(text)
                            try {
                                active.cancelProcess()
                            } catch (_: Throwable) {
                            }
                        }
                    }

                    override fun onDone() {
                        done.complete(acc.toString())
                    }

                    override fun onError(throwable: Throwable) {
                        if (done.isDone) {
                            return
                        }
                        val text = acc.toString()
                        if (text.isNotEmpty()) {
                            done.complete(text)
                        } else {
                            done.completeExceptionally(throwable)
                        }
                    }
                },
                maxOutputToken = MAX_OUTPUT_TOKENS,
                responseFormat = responseFormat,
            )
            val reply = done.get(GENERATE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            logi("sms-ai reply (${reply.length} chars): ${reply.take(80)}")
            return Pair(reply, null)
        } catch (e: TimeoutException) {
            try {
                conversation?.cancelProcess()
            } catch (_: Throwable) {
            }
            return Pair(null, "timeout")
        } catch (t: Throwable) {
            return Pair(null, rootMessage(t))
        } finally {
            try {
                conversation?.close()
            } catch (_: Throwable) {
            }
        }
    }
}

data class SmsAiClassification(
    val category: String?,
    val rawReply: String? = null,
    val error: String? = null,
)

object SmsAiClassifier {
    // Tests can replace inference without loading LiteRT-LM.
    @Volatile
    var override: ((Context, String, String) -> Pair<String?, String?>)? = null

    fun classifySms(ctx: Context, prompt: String, smsContent: String): SmsAiClassification {
        override?.let { fn ->
            val (matched, err) = fn(ctx, prompt, smsContent)
            return SmsAiClassification(category = matched, rawReply = matched, error = err)
        }

        val categories = SmsAiCategoryTable.listAll(ctx)
            .filter { it.isActive() && it.name.isNotBlank() }
        if (categories.isEmpty()) {
            return SmsAiClassification(category = null, error = "no categories")
        }
        val names = categories.map { it.name.trim() }
        val formatted = SmsAiPrompt.format(
            prompt,
            categories.map {
                SmsAiPrompt.PromptCategory(
                    name = it.name.trim(),
                    description = it.description,
                )
            },
            smsContent,
        )
        val constraint = SmsAiPrompt.categoryConstraintRegex(names)?.let { ResponseFormat.regex(it) }
        val (raw, err) = SmsAiEngine.generate(
            ctx,
            formatted,
            { partial -> SmsAiPrompt.shouldStopGeneration(partial, names) },
            constraint,
        )
        if (err != null) {
            return SmsAiClassification(category = null, rawReply = raw, error = err)
        }
        val matched = SmsAiPrompt.matchCategory(raw, names)
        return SmsAiClassification(
            category = matched,
            rawReply = raw,
            error = if (matched == null) "unrecognized category: ${raw?.trim()}" else null,
        )
    }
}
