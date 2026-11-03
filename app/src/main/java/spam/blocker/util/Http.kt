package spam.blocker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import spam.blocker.BuildConfig
import spam.blocker.service.bot.HTTP_GET
import spam.blocker.service.bot.HTTP_POST
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL


data class HttpResult(
    val statusCode: Int? = null,
    val echo: ByteArray? = null,
    val exception: String? = null,
    var cookies: List<String> = listOf(),
)

const val UA = "User-Agent"

// An discardable `HttpURLConnection`.
fun asyncHttpRequest(
    urlString: String,
    headersMap: Map<String, String> = mapOf(),
    method: Int = HTTP_GET,
    postBody: String? = null,
    scope: CoroutineScope = CoroutineScope(IO),
) : Channel<HttpResult> {

    val channel = Channel<HttpResult>()

    scope.launch {

        var conn: HttpURLConnection? = null

        try {
            conn = URL(urlString).openConnection() as HttpURLConnection

            headersMap.forEach { (key, value) ->
                conn.setRequestProperty(key, value)
            }

            if (!headersMap.containsKey(UA)) { // Set default UA
                conn.setRequestProperty(UA, "SpamBlocker/${BuildConfig.VERSION_NAME}")
            }

            // Send POST data
            if (method == HTTP_POST) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                val wr = OutputStreamWriter(conn.outputStream)
                wr.write(postBody ?: "")
                wr.flush()
            }

            val echo = try { // 200
                conn.inputStream.use { it.readBytes() } // this throws FileNotFound when status code == 40x
            } catch (_: Exception) { // 40x read form errorStream instead
                try {
                    conn.errorStream.use { it.readBytes() }
                } catch (_: Exception) {
                    null
                }
            }

            val result = HttpResult(
                statusCode = conn.responseCode,
                echo = echo,
            )

            // handle cookies
            conn.headerFields
                .entries
                .firstOrNull { it.key.equals("Set-Cookie", ignoreCase = true) }
                ?.value
                ?.let { cookies ->
                    result.cookies += cookies
                }

            channel.send(result)
        } catch (e: Exception) {
            channel.send(
                HttpResult(exception = "$e")
            )
        } finally {
            conn?.disconnect()
        }
    }
    return channel
}

// A blocking http request.
// Return null means unknown error.
fun httpRequest(
    urlString: String,
    headersMap: Map<String, String> = mapOf(),
    method: Int = HTTP_GET,
    postBody: String? = null,
    scope: CoroutineScope = CoroutineScope(IO),
) : HttpResult? {

    return runBlocking {
        val resultChannel = asyncHttpRequest(
            scope = scope,
            urlString = urlString,
            headersMap = headersMap,
            method = method,
            postBody = postBody,
        )

        scope.async {
            resultChannel.receiveCatching()
        }.await().getOrNull()
    }
}