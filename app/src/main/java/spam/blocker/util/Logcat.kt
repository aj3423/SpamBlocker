package spam.blocker.util

import android.util.Log
import spam.blocker.G
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val TAG = "SpamBlocker"

private fun now() : String {
    val millis = System.currentTimeMillis()
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val formattedTime = sdf.format(Date(millis))
    return "$millis $formattedTime"
}

fun logd(str: String) {
    G.debugText.value += now() + " " + str + "\n"
}
fun logi(str: String) {
    G.debugText.value += now() + " " + str + "\n"
}
fun logw(str: String) {
    G.debugText.value += now() + " " + str + "\n"
}
fun loge(str: String) {
    G.debugText.value += now() + " " + str + "\n"
}