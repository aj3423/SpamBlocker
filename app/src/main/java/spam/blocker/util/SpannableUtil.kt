package spam.blocker.util

import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import spam.blocker.def.Def


class SpannableUtil {
    companion object {
        fun append(sb: SpannableStringBuilder, strToAppend: String, color: Int, bold: Boolean = false, relativeSize: Float = 0.0f) {
            if (strToAppend.isEmpty())
                return

            val lenBefore = sb.length
            val lenAfter = lenBefore + strToAppend.length

            sb.append(strToAppend)
            sb.setSpan(ForegroundColorSpan(color), lenBefore, lenAfter, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (bold) {
                sb.setSpan(StyleSpan(Typeface.BOLD), lenBefore, lenAfter, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (relativeSize != 0.0f) {
                sb.setSpan(RelativeSizeSpan(relativeSize), lenBefore, lenAfter, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }
}