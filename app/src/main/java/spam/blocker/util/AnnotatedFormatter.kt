package spam.blocker.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

fun String.A(color: Color = Color.Unspecified): AnnotatedString =
    AnnotatedString(this, SpanStyle(color = color))

fun AnnotatedString.A(color: Color = Color.Unspecified): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = color)) {
            append(this@A)
        }
    }

fun String.formatAnnotated(vararg args: AnnotatedString): AnnotatedString {
    return buildAnnotatedString {
        // Split the format string by %s placeholders
        val parts = split("%s")
        var currentIndex = 0

        // Iterate through parts and arguments
        parts.forEachIndexed { index, part ->
            // Append the static part and apply red color
            if (part.isNotEmpty()) {
                append(part)
                addStyle(SpanStyle(color = Color.Unspecified), currentIndex, currentIndex + part.length)
                currentIndex += part.length
            }

            // Append the AnnotatedString argument if available
            if (index < args.size) {
                val arg = args[index]
                append(arg)
                currentIndex += arg.length
            }
        }

        // Append any remaining AnnotatedString arguments
        args.drop(parts.size).forEach { arg ->
            append(arg)
            currentIndex += arg.length
        }
    }
}
