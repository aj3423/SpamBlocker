package spam.blocker.util

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import spam.blocker.ui.theme.CustomColorsPalette
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.Emerald
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SilverGrey
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

// It outputs to the `adb logcat`, for debugging and troubleshooting
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
    override fun success(message: AnnotatedString) = outputAnnotated(message)
    override fun error(message: AnnotatedString) = outputAnnotated(message)
}

@Serializable
data class Markup(
    val start: Int,
    val end: Int,
    val color : Int,
)

// Create an AnnotatedString from `text` and `markups`
fun String.applyAnnotatedMarkups(markups: List<Markup>): AnnotatedString {
    val builder = AnnotatedString.Builder()

    for (markup in markups) {
        builder.append(
            this.substring(markup.start, markup.end).A(Color(markup.color))
        )
    }
    return builder.toAnnotatedString()
}


// A serializable class that can be saved and restored,
//   for saving the logs of workflow runs to database.
@Serializable
data class SaveableLogger(
    var text: String = "",
    val markups: MutableList<Markup> = mutableListOf()
) : ILogger {
    private fun add(message: String, color: Color) {
        val start = text.length
        text += message + "\n"
        val end = text.length

        markups += Markup(start, end, color.toArgb())
    }
    override fun debug(message: String) {
        add(message, SilverGrey)
    }

    override fun info(message: String) {
        add(message, SkyBlue)
    }

    override fun warn(message: String) {
        add(message, DarkOrange)
    }

    override fun success(message: String) {
        add(message, Emerald)
    }

    override fun error(message: String) {
        add(message, Salmon)
    }
    private fun addAnnotated(message: AnnotatedString) {
        text += message.text + "\n"
        message.spanStyles.forEach { span ->
            val start = span.start
            val end = span.end
            val color = span.item.color.toArgb()

            markups += Markup(start, end, color)
        }
    }
    override fun debug(message: AnnotatedString) = addAnnotated(message)
    override fun info(message: AnnotatedString) = addAnnotated(message)
    override fun warn(message: AnnotatedString) = addAnnotated(message)
    override fun success(message: AnnotatedString) = addAnnotated(message)
    override fun error(message: AnnotatedString) = addAnnotated(message)

    fun serialize(): String {
        return Json.encodeToString(this)
    }
    companion object {
        fun parse(jsonStr: String) : SaveableLogger {
            return PermissiveJson.decodeFromString<SaveableLogger>(jsonStr)
        }
    }
}