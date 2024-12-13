package spam.blocker.util

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import spam.blocker.ui.theme.CustomColorsPalette
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.SkyBlue

// For showing detailed execution steps when testing
interface ILogger {
    fun debug(message: String)
    fun info(message: String)
    fun warn(message: String)
    fun success(message: String)
    fun error(message: String)
    fun debug(message: AnnotatedString)
    fun info(message: AnnotatedString)
    fun warn(message: AnnotatedString)
    fun success(message: AnnotatedString)
    fun error(message: AnnotatedString)
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

    override fun debug(message: AnnotatedString) {
        logd(message.text)
    }

    override fun info(message: AnnotatedString) {
        logi(message.text)
    }

    override fun warn(message: AnnotatedString) {
        logw(message.text)
    }

    override fun success(message: AnnotatedString) {
        logd(message.text)
    }

    override fun error(message: AnnotatedString) {
        loge(message.text)
    }
}

// It appends text to a `MutableState<AnnotatedString>` that used by a Text()
class TextLogger(
    private val text: MutableState<AnnotatedString>,
    private val palette: CustomColorsPalette,
) : ILogger {
    private fun output(message: String, defaultColor: Color) {
        text.value = buildAnnotatedString {
            append(text.value) // Append the existing text
            withStyle(style = SpanStyle(color = defaultColor)) {
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

    private fun outputAnnotated(message: AnnotatedString) {
        text.value = buildAnnotatedString {
            append(text.value) // Append the existing text
            append(message)
            append("\n")
        }
    }

    override fun debug(message: AnnotatedString) = outputAnnotated(message)
    override fun info(message: AnnotatedString) = outputAnnotated(message)
    override fun warn(message: AnnotatedString) = outputAnnotated(message)
    override fun success(message: AnnotatedString) =outputAnnotated(message)
    override fun error(message: AnnotatedString) = outputAnnotated(message)
}
