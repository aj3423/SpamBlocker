package spam.blocker.ui.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

class TextDrawable(private val text: String, private val color: Int) : Drawable() {
    private val paint: Paint = Paint()

    init {
        paint.setColor(Color.WHITE)
        paint.textSize = 40f
        paint.isAntiAlias = true
        paint.isFakeBoldText = true
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.textAlign = Paint.Align.LEFT
        paint.color = color
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds

        val x = bounds.centerX().toFloat() - paint.measureText(text) / 2
        val y = bounds.centerY().toFloat() + paint.textSize / 2
        canvas.drawText(text, x, y, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.setAlpha(alpha)
    }

    override fun setColorFilter(cf: ColorFilter?) {
        paint.setColorFilter(cf)
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}