package spam.blocker.ui.widgets

import android.graphics.drawable.Drawable
import android.text.Html
import android.text.method.LinkMovementMethod
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
) {
    val ctx = LocalContext.current

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
