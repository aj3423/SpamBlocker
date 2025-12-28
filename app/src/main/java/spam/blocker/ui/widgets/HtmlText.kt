package spam.blocker.ui.widgets

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import androidx.core.text.parseAsHtml
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda


private class LocalLinkMovementMethod(
    val onRandomClick: Lambda? = null
) : LinkMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        val handled = super.onTouchEvent(widget, buffer, event)

        // Detect random clicks (not clicking links)
        if (event.action == MotionEvent.ACTION_UP) {
            if (!handled) {
                onRandomClick?.let { it() }
            }
        }
        return handled
    }
}

/*
To embed images in the string, use tags like:
    <string name="...">
        <![CDATA[
        Hello <img src=\'ic_hello_world\'/> world.
        ]]>
    </string>
 */
@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textGrey,
    onRandomClick: Lambda? = null,
) {
    val ctx = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(color.toArgb())
                textSize = 15f

                // Handle link clicks, open in browser
                movementMethod = LocalLinkMovementMethod(onRandomClick)
            }
        },
        update = { textView ->
            textView.text =
                html.parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT, { source ->
                    val resourceId =
                        ctx.resources.getIdentifier(source, "drawable", ctx.packageName)
                    if (resourceId != 0) {
                        ctx.resources.getDrawable(resourceId, null).apply {
                            setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                        }
                    } else {
                        null
                    }
                })
        }
    )
}
