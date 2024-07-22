package spam.blocker.ui.util

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import spam.blocker.R


// CheckBox is TextView
fun applyBasicStyle(ctx: Context, view: TextView) {
    view.apply {
        setTypeface(typeface, Typeface.BOLD)
        textSize = 16F
        setTextColor(ctx.getColor(R.color.text_grey))
        setPadding(32, 16, 32, 16)
    }
}

interface MenuItem  {
    fun build(ctx: Context) : View
    fun sticky(): Boolean
}

class Button(
    private val label : String,
) : MenuItem {
    override fun sticky() : Boolean {
        return false
    }

    override fun build(ctx: Context) : View {
        return TextView(ctx).apply {
            text = label
            applyBasicStyle(ctx, this)
        }
    }
}
class Check(
    private val label : String,
    val checked: Boolean,
) : MenuItem {

    private lateinit var checkbox: CheckBox

    override fun sticky() : Boolean {
        return true
    }

    override fun build(ctx: Context) : View {
        checkbox = CheckBox(ctx).apply {
            text = label
            isChecked = checked
            applyBasicStyle(ctx, this)
        }
        return checkbox
    }
}

fun dynamicPopupMenu(
    ctx: Context,
    anchor: View?,
    displayMenuItems: List<MenuItem>,
    onItemClick: ((Int) -> Unit)? = null,
    onItemCheck: ((Int, Boolean) -> Unit)? = null,
) {
    lateinit var popup : PopupWindow

    val inflater = LayoutInflater.from(ctx)
    val root = inflater.inflate(R.layout.dynamic_menu, null) as LinearLayout
    root.setPadding(8, 8, 8, 8)

    displayMenuItems.forEachIndexed { index, item ->
        val itemView = item.build(ctx)

        itemView.setOnClickListener {
            // 1. close popup
            if(!item.sticky()) {
                popup.dismiss()
            }

            // 2. trigger the click event
            onItemClick?.let { it(index) }

            // 3. trigger the check event
            if (itemView is CheckBox) {
                onItemCheck?.let { it(index, itemView.isChecked) }
            }
        }
        root.addView(itemView)
    }

    root.measure(WRAP_CONTENT, WRAP_CONTENT)
    popup = PopupWindow(root, root.measuredWidth, root.measuredHeight).apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT));

        isTouchable = true
        isFocusable = true
        overlapAnchor = false
        width = root.measuredWidth
        height = root.measuredHeight
        contentView = root
    }

    popup.showAsDropDown(anchor)
}