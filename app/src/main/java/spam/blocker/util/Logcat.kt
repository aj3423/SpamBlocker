package spam.blocker.util

import android.util.Log
import spam.blocker.G

const val TAG = "SpamBlocker"

fun logd(str: String) {
    G.debugText.value += str + "\n"
}
fun logi(str: String) {
    G.debugText.value += str + "\n"
}
fun logw(str: String) {
    G.debugText.value += str + "\n"
}
fun loge(str: String) {
    G.debugText.value += str + "\n"
}