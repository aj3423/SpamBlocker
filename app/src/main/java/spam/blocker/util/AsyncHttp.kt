package spam.blocker.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit


data class HttpResult(
    val statusCode: Int? = null,
    val bytes: ByteArray? = null,
    val exception: String? = null,
) {
    fun succeeded(): Boolean {
        return statusCode == 200
    }
}

// An discardable `HttpURLConnection`.
fun asyncHttpRequest(
    urlString: String,
    headersMap: Map<String, String> = mapOf(),
    scope: CoroutineScope = CoroutineScope(IO),
) : Channel<HttpResult> {

    val channel = Channel<HttpResult>()

    scope.launch {

        var connection: HttpURLConnection? = null

        try {
            connection = URL(urlString).openConnection() as HttpURLConnection

            headersMap.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }

            connection.connect()

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val bytes = connection.inputStream.use { it.readBytes() }
                channel.send(
                    HttpResult(
                        statusCode = connection.responseCode,
                        bytes = bytes,
                    )
                )
            } else {
                channel.send(
                    HttpResult(
                        statusCode = connection.responseCode,
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
            connection?.disconnect()
        }
    }
    return channel
}
