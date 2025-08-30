package spam.blocker.ui.widgets

import android.graphics.drawable.Drawable
import android.text.Html
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
import spam.blocker.util.Lambda1

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
    onLinkClick: Lambda1<String>? = null,
) {
    val ctx = LocalContext.current

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                setTextColor(color.toArgb())
                textSize = 15f

                // Handle link clicks
                // it supports links like:
                //   <a href=\'#\'>Tap this</a>
                //
                // - Open the link in browser if the href starts with "http"
                // - Otherwise call the onLinkClick callback
                movementMethod = object : LinkMovementMethod() {
                    override fun onTouchEvent(
                        widget: TextView,
                        buffer: Spannable,
                        event: MotionEvent
                    ): Boolean {
                        if (event.action == MotionEvent.ACTION_UP) {
                            val x = event.x - widget.totalPaddingLeft + widget.scrollX
                            val y = event.y - widget.totalPaddingTop + widget.scrollY
                            val layout = widget.layout
                            if (layout != null) {
                                val line = layout.getLineForVertical(y.toInt())
                                val offset = layout.getOffsetForHorizontal(line, x)
                                val spans = buffer.getSpans(offset, offset, URLSpan::class.java)
                                if (spans.isNotEmpty()) {
                                    val href = spans[0].url // Get href as ID
                                    if (href.startsWith("http")) {
                                        spans[0].onClick(widget) // open in browser
                                    } else {
                                        // Trigger callback for custom IDs
                                        onLinkClick?.let { it(href) }
                                    }
                                    return true
                                }
                            }
                        }
                        return super.onTouchEvent(widget, buffer, event)
                    }
                }
            }
        },
        update = { textView ->
            textView.text =
                html.parseAsHtml(HtmlCompat.FROM_HTML_MODE_COMPACT, object : Html.ImageGetter {
                    override fun getDrawable(source: String?): Drawable? {
                        val resourceId =
                            ctx.resources.getIdentifier(source, "drawable", ctx.packageName)
                        return if (resourceId != 0) {
                            ctx.resources.getDrawable(resourceId, null).apply {
                                setBounds(0, 0, intrinsicWidth, intrinsicHeight)
                            }
                        } else {
                            null
                        }
                    }
                })
        }
    )
}
