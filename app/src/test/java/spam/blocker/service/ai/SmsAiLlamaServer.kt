package spam.blocker.service.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

class SmsAiLlamaServer(
    private val model: File,
    private val port: Int = System.getProperty("smsAiPort")?.toIntOrNull() ?: 18742,
    private val ngl: Int = System.getProperty("smsAiNgl")?.toIntOrNull()
        ?: System.getenv("SMS_AI_NGL")?.toIntOrNull()
        ?: 0,
    private val parallel: Int = System.getProperty("smsAiParallel")?.toIntOrNull()
        ?: System.getenv("SMS_AI_PARALLEL")?.toIntOrNull()
        ?: 1,
) : AutoCloseable {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()
    private var process: Process? = null
    private var log: File? = null
    private var startedHere = false

    val baseUrl: String get() = "http://127.0.0.1:$port"

    fun ensureRunning(): SmsAiLlamaServer {
        if (isHealthy()) {
            return this
        }
        start()
        return this
    }

    fun start() {
        if (isHealthy()) {
            return
        }
        val bin = llamaServerBin()
        val slots = parallel.coerceAtLeast(1)
        val cacheReuse = System.getProperty("smsAiCacheReuse")?.toIntOrNull()
            ?: System.getenv("SMS_AI_CACHE_REUSE")?.toIntOrNull()
            ?: 64
        val logFile = File.createTempFile("llama-server-", ".log")
        log = logFile
        val pb = ProcessBuilder(
            bin,
            "-m", model.absolutePath,
            "--host", "127.0.0.1",
            "--port", port.toString(),
            "--temp", "0",
            "--top-k", "1",
            // Without --kv-unified, -c is split across slots (2048/8=256) and SMS
            // prompts overflow. Unified KV is the continuous-batching setup.
            "-c", (2048 * slots).toString(),
            "-n", "24",
            "-ngl", ngl.toString(),
            "--parallel", slots.toString(),
            "--kv-unified",
            "--cont-batching",
            // Gemma 3 SWA drops prefix KV unless the SWA cache is full-size.
            "--swa-full",
            "--cache-prompt",
            "--cache-reuse", cacheReuse.toString(),
            "--log-disable",
        )
        pb.redirectOutput(logFile)
        pb.redirectErrorStream(true)
        val proc = pb.start()
        process = proc
        startedHere = true
        try {
            waitForHealth()
        } catch (t: Throwable) {
            stop()
            val tail = logFile.readText().takeLast(4000)
            throw IllegalStateException("llama-server failed to start:\n$tail", t)
        }
    }

    fun complete(prompt: String, grammar: String? = null): String {
        val payload = buildJsonObject {
            put("temperature", 0.0)
            put("top_k", 1)
            put("max_tokens", 24)
            if (!grammar.isNullOrBlank()) {
                put("grammar", grammar)
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "user")
                    put("content", prompt)
                }
            }
        }
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(60))
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build()
        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() !in 200..299) {
            throw IllegalStateException("llama-server HTTP ${resp.statusCode()}: ${resp.body()}")
        }
        val json = Json.parseToJsonElement(resp.body()).jsonObject
        val content = json["choices"]
            ?.jsonArray
            ?.getOrNull(0)
            ?.jsonObject
            ?.get("message")
            ?.jsonObject
            ?.get("content")
            ?.jsonPrimitive
            ?.contentOrNull
        return content ?: throw IllegalStateException("no content in ${resp.body()}")
    }

    fun isHealthy(): Boolean {
        return try {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/health"))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build()
            val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() !in 200..299) {
                return false
            }
            Json.parseToJsonElement(resp.body()).jsonObject["status"]?.jsonPrimitive?.contentOrNull == "ok"
                || resp.statusCode() == 200
        } catch (_: Exception) {
            false
        }
    }

    private fun waitForHealth() {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90)
        while (System.nanoTime() < deadline) {
            val proc = process
            if (proc != null && !proc.isAlive) {
                throw IllegalStateException("llama-server exited ${proc.exitValue()}")
            }
            if (isHealthy()) {
                return
            }
            Thread.sleep(200)
        }
        throw IllegalStateException("llama-server health timeout")
    }

    fun stop() {
        val proc = process ?: return
        proc.destroy()
        if (!proc.waitFor(5, TimeUnit.SECONDS)) {
            proc.destroyForcibly()
        }
        process = null
        startedHere = false
    }

    override fun close() {
        if (startedHere) {
            stop()
        }
    }

    companion object {
        fun findModel(): File? {
            System.getProperty("smsAiModel")?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
            System.getenv("SMS_AI_MODEL")?.let { File(it).takeIf { f -> f.isFile } }?.let { return it }
            var dir = File(System.getProperty("user.dir") ?: ".")
            repeat(6) {
                val candidate = File(dir, ".models/sms-ai/gemma-3-1b-it-Q4_K_M.gguf")
                if (candidate.isFile) {
                    return candidate
                }
                dir = dir.parentFile ?: return null
            }
            return null
        }

        fun llamaServerBin(): String {
            System.getProperty("smsAiLlamaServer")?.takeIf { it.isNotBlank() }?.let { return it }
            System.getenv("SMS_AI_LLAMA_SERVER")?.takeIf { it.isNotBlank() }?.let { return it }
            return "llama-server"
        }
    }
}
