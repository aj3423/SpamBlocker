package spam.blocker.ui.util

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.LifecycleOwner
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.R


/**
 * Call this method (in onActivityCreated or later) to set
 * the width of the dialog to a percentage of the current
 * screen width.
 */
fun DialogFragment.setWidthPercent(percentage: Int) {
    val percent = percentage.toFloat() / 100
    val dm = Resources.getSystem().displayMetrics
    val rect = dm.run { Rect(0, 0, widthPixels, heightPixels) }
    val percentWidth = rect.width() * percent
    dialog?.window?.setLayout(percentWidth.toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
}

/**
 * Call this method (in onActivityCreated or later)
 * to make the dialog near-full screen.
 */
fun DialogFragment.setFullScreen() {
    dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
}

class UI {
    companion object {

        fun delay(millis: Int, action: ()->Unit) {
            CoroutineScope(Dispatchers.Default).launch {
                kotlinx.coroutines.delay(100)
                withContext(Dispatchers.Main) {
                    action()
                }
            }
        }
        fun showIf(view: View, visible: Boolean, hide: Boolean = false) {
            view.visibility = if(visible)
                View.VISIBLE
            else if (hide)
                View.INVISIBLE
            else
                View.GONE
        }
        fun applyTheme(themeType: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when(themeType) {
                    1 -> MODE_NIGHT_NO
                    2 -> MODE_NIGHT_YES
                    else -> MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

        // for round avatar
        fun setRoundImage(imageView: ImageView, bitmap: Bitmap) {
            val circularBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(circularBitmap)
            val paint = Paint().apply {
                shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                isAntiAlias = true
                isDither = true
                isFilterBitmap = true
            }
            val radius = bitmap.width.coerceAtMost(bitmap.height) / 2f
            canvas.drawCircle(bitmap.width / 2f, bitmap.height / 2f, radius, paint)
            imageView.setImageBitmap(circularBitmap)
        }

        fun preventMenuClosingWhenItemClicked(ctx: Context, item: MenuItem) {
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_COLLAPSE_ACTION_VIEW)
            item.actionView = View(ctx)
            item.setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return false
                }

                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    return false
                }
            })
        }

        // setup the hint from the imgView.tooltipText
        fun createBalloon(
            ctx: Context,
            viewLifecycleOwner: LifecycleOwner,
            strId: Int) : Balloon
        {

            val balloon = Balloon.Builder(ctx)
                .setHeight(BalloonSizeSpec.WRAP)
                .setText(ctx.resources.getText(strId))
                .setTextIsHtml(true)
                .setTextColorResource(R.color.white)
                .setTextSize(15f)
                .setArrowPositionRules(ArrowPositionRules.ALIGN_ANCHOR)
                .setArrowSize(10)
                .setPadding(8)
                .setTextGravity(Gravity.START)
                .setCornerRadius(8f)
                .setBackgroundColor(ctx.resources.getColor(R.color.tooltip_blue, null))
                .setBalloonAnimation(BalloonAnimation.ELASTIC)
                .setLifecycleOwner(viewLifecycleOwner)
                .setIsAttachedInDecor(false)

                .build()
            return balloon
        }
        fun setupImageTooltip(
            ctx: Context,
            viewLifecycleOwner: LifecycleOwner,
            imgView: ImageView,
            strId: Int
        ) {
            val balloon = createBalloon(ctx, viewLifecycleOwner, strId)
            imgView.setOnClickListener {
                balloon.showAlignBottom(imgView)
            }
        }
    }
}