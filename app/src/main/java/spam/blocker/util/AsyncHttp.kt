package spam.blocker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
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


// An discardable `HttpURLConnection`.
fun asyncHttpRequest(
    urlString: String,
    headersMap: Map<String, String> = mapOf(),
    method: Int = HTTP_GET,
    postBody: String = "",
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

            // Send POST data
            if (method == HTTP_POST) {
                conn.requestMethod = "POST"
                conn.doOutput = true
                val wr = OutputStreamWriter(conn.outputStream)
                wr.write(postBody)
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
