package spam.blocker.ui.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.LifecycleOwner
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import spam.blocker.R

class Util {
    companion object {

        fun applyTheme(dark: Boolean) {
            if (dark) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
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
        fun setupImageTooltip(
            ctx: Context,
            viewLifecycleOwner: LifecycleOwner,
            imgView: ImageView,
            strId: Int
        ) {
            imgView.setOnClickListener {
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

                balloon.showAlignBottom(imgView)
            }
        }
    }
}