package spam.blocker.ui.widgets

import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
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
import spam.blocker.util.Lambda1


private class LocalLinkMovementMethod(
    val onRandomClick: Lambda? = null,
    val onCustomLinkClick: Lambda1<String>? = null
) : LinkMovementMethod() {

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        var handled = super.onTouchEvent(widget, buffer, event)

        // Detect random clicks (not clicking links)
        if (event.action == MotionEvent.ACTION_UP) {
            handled = handleCustomTagClicking(widget, buffer, event)
            if (!handled) {
                onRandomClick?.let { it() }
            }
        }
        return handled
    }
    fun handleCustomTagClicking(widget: TextView, buffer: Spannable, event: MotionEvent) : Boolean {
        val x = event.x - widget.totalPaddingLeft + widget.scrollX
        val y = event.y - widget.totalPaddingTop + widget.scrollY
        val layout = widget.layout
        if (layout != null) {
            val line = layout.getLineForVertical(y.toInt())
            val offset = layout.getOffsetForHorizontal(line, x)
            val spans = buffer.getSpans(offset, offset, URLSpan::class.java)
            if (spans.isNotEmpty()) {
                val href = spans[0].url // Get href as ID
                if (!href.startsWith("http")) {
                    // Trigger callback for custom IDs
                    onCustomLinkClick?.let { it(href) }
                    return true
                }
            }
        }
        return false
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
    onCustomLinkClick: Lambda1<String>? = null,
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
                movementMethod = LocalLinkMovementMethod(
                    onRandomClick = onRandomClick,
                    onCustomLinkClick = onCustomLinkClick
                )
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
