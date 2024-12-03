package spam.blocker.util

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import spam.blocker.ui.theme.CustomColorsPalette
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.DodgeBlue
import spam.blocker.ui.theme.SkyBlue

// For showing detailed execution steps when testing Workflows
interface ILogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun success(message: String)
    fun error(message: String)
}

// It outputs to the `adb logcat`
class AdbLogger : ILogger {
    override fun debug(message: String) {
        logd(message)
    }
    override fun info(message: String) {
        logi(message)
    }

    override fun warn(message: String) {
        logw(message)
    }

    override fun success(message: String) {
        logd(message)
    }

    override fun error(message: String) {
        loge(message)
    }
}

// It appends text to a `MutableState<AnnotatedString>` that used by a Text()
class TextLogger(
    private val text: MutableState<AnnotatedString>,
    private val palette: CustomColorsPalette,
) : ILogger {
    private fun output(message: String, color: Color) {
        text.value = buildAnnotatedString {
            append(text.value) // Append the existing text
            withStyle(style = SpanStyle(color = color)) {
                append("$message\n")
            }
        }
    }
    override fun debug(message: String) {
        output(message, palette.textGrey)
    }

    override fun info(message: String) {
        output(message, SkyBlue)
    }

    override fun warn(message: String) {
        output(message, DarkOrange)
    }

    override fun success(message: String) {
        output(message, palette.pass)
    }

    override fun error(message: String) {
        output(message, palette.block)
    }
}
