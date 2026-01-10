package spam.blocker.ui.main


import android.content.Context
import spam.blocker.util.logi

fun debug(ctx: Context) {
    val x = "(?!texas$).*".toRegex().matches("")

    logi("matches: $x")

}