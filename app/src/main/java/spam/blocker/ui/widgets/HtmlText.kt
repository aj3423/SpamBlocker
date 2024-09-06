package spam.blocker.ui.widgets

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import spam.blocker.ui.theme.LocalPalette

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textGrey,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(color.toArgb())
                textSize = 15f

                // make links clickable
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = {
            it.text = HtmlCompat.fromHtml(
                html,
                HtmlCompat.FROM_HTML_MODE_COMPACT
            )
        }
    )
}
