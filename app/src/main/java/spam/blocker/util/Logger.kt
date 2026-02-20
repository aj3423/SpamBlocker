package spam.blocker.util

import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import kotlinx.serialization.Serializable
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

// It appends text to a `MutableState<AnnotatedString>` that used by a `@Composable Text()`
class JetpackTextLogger(
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

    private fun outputAnnotated(message: AnnotatedString, color: Color) {
        text.value = buildAnnotatedString {
            append(text.value) // Append the existing text
            withStyle(style = SpanStyle(color = color)) {
                append(message)
                append("\n")
            }
        }
    }

    override fun debug(message: AnnotatedString) = outputAnnotated(message, palette.textGrey)
    override fun info(message: AnnotatedString) = outputAnnotated(message, SkyBlue)
    override fun warn(message: AnnotatedString) = outputAnnotated(message, DarkOrange)
    override fun success(message: AnnotatedString) = outputAnnotated(message, palette.pass)
    override fun error(message: AnnotatedString) = outputAnnotated(message, palette.block)
}

@Serializable
data class Markup(
    val start: Int,
    val end: Int,
    val color : Int,
)

@Serializable
data class MarkupText(
    var text: String = "",
    val markups: MutableList<Markup> = mutableListOf(),
) {
    fun serialize(): String {
        return try {
            Json.encodeToString(this)
        } catch (_: Exception) {
            ""
        }
    }

    // Create an AnnotatedString from the text and markups
    fun toAnnotatedString(): AnnotatedString {
        return buildAnnotatedString {
            append(text)
            markups.forEach { markup ->
                addStyle(
                    style = SpanStyle(color = Color(markup.color)),
                    start = markup.start,
                    end = markup.end
                )
            }
        }
    }

    companion object {
        private val PermissiveJson = Json { ignoreUnknownKeys = true }
        fun parse(jsonStr: String): MarkupText {
            return PermissiveJson.decodeFromString(jsonStr)
        }
    }
}

// Serializable logger class
class SaveableLogger(
    private val palette: CustomColorsPalette,
    val output : MarkupText = MarkupText()
) : ILogger {
    private fun add(message: String, color: Color) {
        val start = output.text.length
        output.text += message + "\n"
        val end = output.text.length
        output.markups += Markup(start, end, color.toArgb())
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

    private fun addAnnotated(message: AnnotatedString, defaultColor: Color) {
        val startOffset = output.text.length

        // 1. Append plain text
        output.text += message.text + "\n"

        // 2. Apply default color to the whole new line first
        output.markups += Markup(
            start = startOffset,
            end = startOffset + message.text.length,
            color = defaultColor.toArgb()
        )

        // 3. Apply block colors that override the defaultColor
        message.spanStyles.forEach { span ->
            val start = startOffset + span.start
            val end = startOffset + span.end
            val color = span.item.color
            if (color != Color.Unspecified) {
                output.markups += Markup(start, end, color.toArgb())
            }
        }
    }

    override fun debug(message: AnnotatedString) = addAnnotated(message, palette.textGrey)
    override fun info(message: AnnotatedString) = addAnnotated(message, SkyBlue)
    override fun warn(message: AnnotatedString) = addAnnotated(message, DarkOrange)
    override fun success(message: AnnotatedString) = addAnnotated(message, palette.pass)
    override fun error(message: AnnotatedString) = addAnnotated(message, palette.block)
}


// It outputs to the `adb logcat`, for debugging and troubleshooting
class MultiLogger(
    val loggers: List<ILogger>
) : ILogger {
    override fun debug(message: String) {
        loggers.forEach { it.debug(message) }
    }

    override fun info(message: String) {
        loggers.forEach { it.info(message) }
    }

    override fun warn(message: String) {
        loggers.forEach { it.warn(message) }
    }

    override fun success(message: String) {
        loggers.forEach { it.success(message) }
    }

    override fun error(message: String) {
        loggers.forEach { it.error(message) }
    }

    override fun debug(message: AnnotatedString) {
        loggers.forEach { it.debug(message) }
    }

    override fun info(message: AnnotatedString) {
        loggers.forEach { it.info(message) }
    }

    override fun warn(message: AnnotatedString) {
        loggers.forEach { it.warn(message) }
    }

    override fun success(message: AnnotatedString) {
        loggers.forEach { it.success(message) }
    }

    override fun error(message: AnnotatedString) {
        loggers.forEach { it.error(message) }
    }
}

fun ILogger.getSaveableOutput(): MarkupText? =
    when (this) {
        is SaveableLogger -> this.output
        is MultiLogger -> {
            val log = this.loggers.firstOrNull { it is SaveableLogger } as? SaveableLogger
            log?.output
        }
        else -> null
    }