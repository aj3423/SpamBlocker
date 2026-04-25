package spam.blocker.ui.widgets

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import spam.blocker.ui.slightDiff
import java.lang.ref.WeakReference

private const val AnimationDurationMs: Long = 120L
private const val AnimationStartScale: Float = 0.8f
private const val BorderWidthDp: Int = 1
private const val PaddingDp: Int = 20
private const val CornerRadiusDp: Float = 5f
private const val MinWidthDp: Int = 220
private const val MinHeightDp: Int = 120


// For Caller ID use only
object FloatingWindow {
    // configurations, updated via `update()`
    private var backgroundColor: Int = 0

    // private attributes
    var onDrag: ((Int, Int) -> Unit)? = null
    private var floatingViewRef: WeakReference<FrameLayout>? = null

    private var isHiding = false
    private var centerX: Int? = null
    private var centerY: Int? = null

    // null == unchanged
    fun update(
        ctx: Context,
        bgColor: Int? = null,
        x: Int? = null,
        y: Int? = null,
    ) {
        // 1. Save attributes if provided
        if (bgColor != null) {
            this.backgroundColor = bgColor
        }
        if (x != null) {
            this.centerX = x
        }
        if (y != null) {
            this.centerY = y
        }

        // 2. Apply changes
        val overlay = floatingView() ?: return
        val layoutParams = overlay.layoutParams as? WindowManager.LayoutParams ?: return

        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT

        applyConfigToOverlay(ctx, overlay)
        windowManager(ctx).updateViewLayout(overlay, layoutParams)
        overlay.post { reconcileOverlayLayout(ctx, overlay, layoutParams) }
    }

    fun show(
        ctx: Context,
        view: View,
    ) {
        // Remove the existing one
        hide(ctx, animated = false)

        val overlayContainer = FrameLayout(ctx).apply {
            clipToOutline = true
            visibility = View.INVISIBLE
            applyConfigToOverlay(ctx, this)
            addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                )
            )
        }

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or // important
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            ,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        attachDragBehavior(ctx, overlayContainer, layoutParams)
        overlayContainer.addOnLayoutChangeListener { view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val width = right - left
            val height = bottom - top
            if ((right - left) == (oldRight - oldLeft) && (bottom - top) == (oldBottom - oldTop)) {
                return@addOnLayoutChangeListener
            }
            if (width <= 0 || height <= 0) {
                return@addOnLayoutChangeListener
            }

            reconcileOverlayLayout(ctx, view, layoutParams)

            if (view.visibility != View.VISIBLE) {
                view.visibility = View.VISIBLE
                animateShow(view)
            }
        }
        windowManager(ctx).addView(overlayContainer, layoutParams)
        floatingViewRef = WeakReference(overlayContainer)
        isHiding = false
    }

    fun hide(ctx: Context) {
        hide(ctx, animated = true)
    }

    private fun hide(ctx: Context, animated: Boolean) {
        floatingView()?.let { overlay ->
            if (isHiding && animated) {
                return
            }

            overlay.animate().cancel()
            overlay.animate().setListener(null)

            if (!animated) {
                removeFloatingView(ctx, overlay)
                return
            }

            isHiding = true
            overlay.isEnabled = false
            overlay.isClickable = false
            overlay.animate()
                .scaleX(AnimationStartScale)
                .scaleY(AnimationStartScale)
                .setDuration(AnimationDurationMs)
                .setListener(object : AnimatorListenerAdapter() {
                    private var canceled = false

                    override fun onAnimationCancel(animation: Animator) {
                        canceled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        overlay.animate().setListener(null)
                        if (!canceled) {
                            removeFloatingView(ctx, overlay)
                        }
                    }
                })
                .start()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragBehavior(
        ctx: Context,
        overlayView: View,
        layoutParams: WindowManager.LayoutParams
    ) {
        var startX = 0
        var startY = 0
        var touchDownX = 0f
        var touchDownY = 0f

        overlayView.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = layoutParams.x
                    startY = layoutParams.y
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val nextLeft = startX + (event.rawX - touchDownX).toInt()
                    val nextTop = startY + (event.rawY - touchDownY).toInt()
                    val overlayWidth = currentOverlayWidth(ctx, overlayView)
                    val overlayHeight = currentOverlayHeight(ctx, overlayView)
                    val boundedLeft = nextLeft.coerceIn(0, maxOverlayX(ctx, overlayWidth))
                    val boundedTop = nextTop.coerceIn(0, maxOverlayY(ctx, overlayHeight))

                    if (boundedLeft != layoutParams.x || boundedTop != layoutParams.y) {
                        layoutParams.x = boundedLeft
                        layoutParams.y = boundedTop
                        windowManager(ctx).updateViewLayout(view, layoutParams)
                        saveCenterPosition(
                            centerXFromOverlayLeft(boundedLeft, overlayWidth),
                            centerYFromOverlayTop(boundedTop, overlayHeight)
                        )
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    val overlayWidth = currentOverlayWidth(ctx, overlayView)
                    val overlayHeight = currentOverlayHeight(ctx, overlayView)
                    saveCenterPosition(
                        centerXFromOverlayLeft(layoutParams.x, overlayWidth),
                        centerYFromOverlayTop(layoutParams.y, overlayHeight)
                    )
                    true
                }

                else -> false
            }
        }
    }

    private fun applyConfigToOverlay(ctx: Context, overlay: FrameLayout) {
        overlay.background = createOverlayBackground(ctx)
        overlay.minimumWidth = minWidth(ctx)
        overlay.minimumHeight = minHeight(ctx)
        val padding = dpToPx(ctx, PaddingDp)
        overlay.setPadding(padding, padding, padding, padding)
    }

    private fun createOverlayBackground(ctx: Context): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(ctx, CornerRadiusDp)
            setColor(backgroundColor)
            setStroke(dpToPx(ctx, BorderWidthDp), Color(backgroundColor).slightDiff().toArgb())
        }
    }

    private fun dpToPx(ctx: Context, dp: Int): Int {
        return (dp * ctx.resources.displayMetrics.density).toInt()
    }

    private fun dpToPx(ctx: Context, dp: Float): Float {
        return dp * ctx.resources.displayMetrics.density
    }

    private fun defaultCenterX(ctx: Context): Int {
        return ctx.resources.displayMetrics.widthPixels / 2
    }

    private fun defaultCenterY(ctx: Context): Int {
        return ctx.resources.displayMetrics.heightPixels / 2
    }

    private fun maxOverlayX(ctx: Context, overlayWidth: Int): Int {
        val screenWidth = ctx.resources.displayMetrics.widthPixels
        return (screenWidth - overlayWidth).coerceAtLeast(0)
    }

    private fun maxOverlayY(ctx: Context, overlayHeight: Int): Int {
        val screenHeight = ctx.resources.displayMetrics.heightPixels
        return (screenHeight - overlayHeight).coerceAtLeast(0)
    }

    private fun boundCenterX(ctx: Context, centerX: Int, overlayWidth: Int): Int {
        val minCenterX = overlayWidth / 2
        val maxCenterX = (ctx.resources.displayMetrics.widthPixels - overlayWidth + minCenterX)
            .coerceAtLeast(minCenterX)
        return centerX.coerceIn(minCenterX, maxCenterX)
    }

    private fun boundCenterY(ctx: Context, centerY: Int, overlayHeight: Int): Int {
        val minCenterY = overlayHeight / 2
        val maxCenterY = (ctx.resources.displayMetrics.heightPixels - overlayHeight + minCenterY)
            .coerceAtLeast(minCenterY)
        return centerY.coerceIn(minCenterY, maxCenterY)
    }

    private fun overlayLeftFromCenter(centerX: Int, overlayWidth: Int): Int {
        return centerX - (overlayWidth / 2)
    }

    private fun overlayTopFromCenter(centerY: Int, overlayHeight: Int): Int {
        return centerY - (overlayHeight / 2)
    }

    private fun centerXFromOverlayLeft(left: Int, overlayWidth: Int): Int {
        return left + (overlayWidth / 2)
    }

    private fun centerYFromOverlayTop(top: Int, overlayHeight: Int): Int {
        return top + (overlayHeight / 2)
    }

    private fun minWidth(ctx: Context): Int {
        return dpToPx(ctx, MinWidthDp)
    }

    private fun minHeight(ctx: Context): Int {
        return dpToPx(ctx, MinHeightDp)
    }

    private fun currentOverlayWidth(ctx: Context, overlay: View): Int {
        return overlay.width
            .takeIf { it > 0 }
            ?: overlay.measuredWidth.takeIf { it > 0 }
            ?: minWidth(ctx)
    }

    private fun currentOverlayHeight(ctx: Context, overlay: View): Int {
        return overlay.height
            .takeIf { it > 0 }
            ?: overlay.measuredHeight.takeIf { it > 0 }
            ?: minHeight(ctx)
    }

    private fun reconcileOverlayLayout(
        ctx: Context,
        overlay: View,
        layoutParams: WindowManager.LayoutParams
    ) {
        if (!overlay.isAttachedToWindow) {
            return
        }

        val overlayWidth = currentOverlayWidth(ctx, overlay)
        val overlayHeight = currentOverlayHeight(ctx, overlay)
        val boundedCenterX = boundCenterX(ctx, centerX ?: defaultCenterX(ctx), overlayWidth)
        val boundedCenterY = boundCenterY(ctx, centerY ?: defaultCenterY(ctx), overlayHeight)
        val boundedLeft = overlayLeftFromCenter(boundedCenterX, overlayWidth)
        val boundedTop = overlayTopFromCenter(boundedCenterY, overlayHeight)

        saveCenterPosition(boundedCenterX, boundedCenterY, notify = false)

        if (boundedLeft == layoutParams.x && boundedTop == layoutParams.y) {
            return
        }

        layoutParams.x = boundedLeft
        layoutParams.y = boundedTop
        windowManager(ctx).updateViewLayout(overlay, layoutParams)
    }

    private fun animateShow(overlay: View) {
        overlay.animate().cancel()
        overlay.animate().setListener(null)

        overlay.scaleX = AnimationStartScale
        overlay.scaleY = AnimationStartScale
        overlay.animate()
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(AnimationDurationMs)
            .start()
    }

    private fun removeFloatingView(ctx: Context, overlay: View) {
        if (floatingView() !== overlay) {
            return
        }

        try {
            overlay.animate().cancel()
            overlay.animate().setListener(null)
            windowManager(ctx).removeViewImmediate(overlay)
        } finally {
            floatingViewRef?.clear()
            floatingViewRef = null
            isHiding = false
        }
    }

    private fun saveCenterPosition(centerX: Int, centerY: Int, notify: Boolean = true) {
        this.centerX = centerX
        this.centerY = centerY

        if (notify) {
            onDrag?.invoke(centerX, centerY)
        }
    }

    private fun windowManager(ctx: Context): WindowManager {
        return ctx.getSystemService(WindowManager::class.java)
    }

    private fun floatingView(): FrameLayout? {
        return floatingViewRef?.get()
    }
}
