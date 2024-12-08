package spam.blocker.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle

fun String.A(color: Color = Color.Unspecified): AnnotatedString =
    AnnotatedString(this, SpanStyle(color = color))

fun String.formatAnnotated(vararg args: AnnotatedString): AnnotatedString {
    // Split the format string by placeholders
    val parts = split("%s")
    val builder = AnnotatedString.Builder()

    // Match each part with annotations
    var annotationIndex = 0
    for (i in parts.indices) {
        builder.append(parts[i])
        if (annotationIndex < args.size) {
            builder.append(args[annotationIndex])
            annotationIndex++
        }
    }

    // Handle case where there might be more placeholders than annotations provided
    while (annotationIndex < args.size) {
        builder.append(args[annotationIndex])
        annotationIndex++
    }

    return builder.toAnnotatedString()
}
