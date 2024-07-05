package spam.blocker.ui.util

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import spam.blocker.R

fun dynamicPopupMenu(
    ctx: Context,
    displayItems: List<String>,
    anchor: View,
    callback: (i: Int) -> Unit
) {

    var popup : PopupWindow? = null

    val inflater = LayoutInflater.from(ctx)
    val root = inflater.inflate(R.layout.dynamic_menu, null) as LinearLayout

    displayItems.forEachIndexed { index, it ->

        val rowView = TextView(ctx).apply {
            text = it
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ctx.getColor(R.color.text_grey))
            setPadding(32, 16, 32, 16)
        }

        rowView.setOnClickListener {
            popup?.dismiss()

            callback(index)
        }
        root.addView(rowView)
    }

    root.measure(WRAP_CONTENT, WRAP_CONTENT)
    popup = PopupWindow(root, root.measuredWidth, root.measuredHeight).apply {

        setBackgroundDrawable(ColorDrawable(ctx.getColor(R.color.dialog_bg)))
        isTouchable = true
        isFocusable = true
        overlapAnchor = true
        width = root.measuredWidth
        height = root.measuredHeight
        contentView = root
    }

    popup.showAsDropDown(anchor)
}