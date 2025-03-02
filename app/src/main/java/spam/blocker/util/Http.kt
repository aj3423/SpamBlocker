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
    val bytes: ByteArray? = null,
    val exception: String? = null,
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

            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                val bytes = conn.inputStream.use { it.readBytes() }
                channel.send(
                    HttpResult(
                        statusCode = conn.responseCode,
                        bytes = bytes,
                    )
                )
            } else {
                channel.send(
                    HttpResult(
                        statusCode = conn.responseCode,
                    )
                )
            }
        } catch (e: Exception) {
            channel.send(
                HttpResult(
                    exception = "$e",
                )
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