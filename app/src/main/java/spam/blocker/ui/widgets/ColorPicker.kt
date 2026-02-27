package spam.blocker.ui.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.contrastColor
import spam.blocker.ui.parseColorString
import spam.blocker.util.Lambda
import spam.blocker.util.Lambda1
import android.graphics.Color as AndroidColor

// https://proandroiddev.com/color-picker-in-compose-f8c29744705
//  +
// AI

private const val AREA_WIDTH = 260 // .dp

private fun CoroutineScope.collectForPress(
    interactionSource: InteractionSource,
    setOffset: (Offset) -> Unit
) {
    launch {
        interactionSource.interactions.collect { interaction ->
            (interaction as? PressInteraction.Press)
                ?.pressPosition
                ?.let(setOffset)
        }
    }
}

private fun Modifier.emitDragGesture(
    interactionSource: MutableInteractionSource
): Modifier = composed {
    val scope = rememberCoroutineScope()

    pointerInput(Unit) {
        detectDragGestures { input, _ ->
            scope.launch {
                interactionSource.emit(PressInteraction.Press(input.position))
            }
        }
    }.clickable(interactionSource, null) {}
}

private fun DrawScope.drawBitmap(
    bitmap: Bitmap,
    panel: RectF
) {
    drawIntoCanvas {
        it.nativeCanvas.drawBitmap(
            bitmap,
            null,
            panel.toRect(),
            null
        )
    }
}

@Composable
fun ColorButton(
    color: Color?,
    text: String? = null,
    enabled: Boolean = true,
    onClick: Lambda
) {
    val C = G.palette

    Button(
        borderColor = C.textGrey,
        backgroundColor = color ?: Color.Unspecified,
        modifier = M.widthIn(min = 60.dp).height(BUTTON_H.dp),
        content = {
            text?.let {
                Text(
                    text = it,
                    color = if (color == null)
                        C.textGrey
                    else
                        color.contrastColor()
                )
            }
        },
        enabled = enabled,
        onClick = onClick
    )
}

// Show up to 6 colors in a button
@Composable
fun MultiColorButton(
    colors: List<Int>,
    emptyColor: Color,
    onClick: Lambda
) {
    val W = 60
    val H = BUTTON_H

    val first6 = colors.take(6)
    val (w, h) = when (first6.size) {
        2 -> (W / 2 to H)
        3 -> (W / 3 to H)
        4 -> (W / 2 to H / 2)
        5, 6 -> (W / 3 to H / 2)
        else -> (W to H)
    }

    StrokeButton(
        color = Color.Unspecified,
        icon = {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(W.dp)
                    .clip(RoundedCornerShape(BUTTON_CORNER_RADIUS.dp))
                    .background(emptyColor),
                contentAlignment = Alignment.Center
            ) {
                FlowRowSpaced(0, vSpace = 0) {
                    first6.forEach {
                        Box(
                            modifier = M
                                .background(Color(it))
                                // It doesn't appear on some devices without the -0.01,
                                //  seems it just has to be smaller than ... whatever.
                                .width((w - 0.001).dp)
                                .height((h - 0.001).dp)
                        )
                    }
                }
            }
        },
        onClick = onClick
    )
}

@Composable
fun ColorPickerButton(
    color: Color?,
    text: String? = null,
    enabled: Boolean = true,
    clearLabel: String? = null,
    clearColor: Color = G.palette.error,
    okLabel: String = Str(R.string.save),
    onSelect: Lambda1<Color?>
) {
    val trigger = remember { mutableStateOf(false) }

    ColorPickerPopup(
        trigger = trigger,
        // don't use Color.Unspecified here, bc White has alpha value
        initColor = color ?: Color.White,
        clearLabel = clearLabel,
        clearColor = clearColor,
        okLabel = okLabel,
        onSelect = onSelect,
    )

    ColorButton(
        color = color,
        text = text,
        enabled = enabled,
    ) {
        trigger.value = true
    }
}
@Composable
fun ColorPickerPopup(
    trigger: MutableState<Boolean>,
    initColor: Color,
    clearLabel: String?,
    clearColor: Color,
    okLabel: String,
    onSelect: Lambda1<Color?>,
) {
    val C = G.palette

    if (!trigger.value) return

    val initialArgb = initColor.toArgb()
    val initialHsv = FloatArray(3).apply {
        AndroidColor.colorToHSV(initialArgb, this)
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0].coerceIn(0f, 360f)) }
    var sat by remember { mutableFloatStateOf(initialHsv[1].coerceIn(0f, 1f)) }
    var value by remember { mutableFloatStateOf(initialHsv[2].coerceIn(0f, 1f)) }
    var alpha by remember { mutableIntStateOf(initialArgb ushr 24) }

    // We keep last meaningful hue to prevent snapping to 0 when sat → 0
    var lastHue by remember { mutableFloatStateOf(hue) }

    // Update lastHue only when saturation is high enough that hue is meaningful
    LaunchedEffect(sat, hue) {
        if (sat > 0.02f) {           // small threshold — tune if needed
            lastHue = hue
        }
    }

    val currentColor by remember(alpha, hue, sat, value) {
        derivedStateOf {
            val effectiveHue = if (sat > 0.02f) hue else lastHue
            Color(AndroidColor.HSVToColor(alpha, floatArrayOf(effectiveHue, sat, value)))
        }
    }

    var hex by remember(currentColor) {
        mutableStateOf(String.format("%08X", currentColor.toArgb()))
    }

    LaunchedEffect(hex) {
        val parsed = hex.parseColorString() ?: return@LaunchedEffect
        val (newAlpha, newRgb) = parsed
        alpha = newAlpha
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(newRgb, hsv)
        hue = hsv[0]
        sat = hsv[1]
        value = hsv[2]
        // lastHue will be updated via the other LaunchedEffect
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            RowVCenterSpaced(8) {
                if (clearLabel != null) {
                    StrokeButton(
                        label = clearLabel,
                        color = clearColor
                    ) {
                        onSelect(null)
                        trigger.value = false
                    }
                }
                StrokeButton(
                    label = okLabel,
                    color = C.teal200
                ) {
                    onSelect(currentColor)
                    trigger.value = false
                }
            }
        }
    ) {
        SatValPanel(
            hue = hue,
            currentSat = sat,
            currentVal = value,
        ) { newSat, newVal ->
            sat = newSat.coerceIn(0f, 1f)
            value = newVal.coerceIn(0f, 1f)
        }

        HueBar(
            currentHue = hue
        ) { newHue ->
            hue = newHue.coerceIn(0f, 360f)
        }

        // Bottom preview area
        RowVCenterSpaced(2) {
            Spacer(modifier = Modifier.width(32.dp))

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(currentColor),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = hex,
                    onValueChange = { hex = it },
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        color = currentColor.contrastColor()
                    ),
                    modifier = Modifier
                        .height(200.dp)
                        .background(currentColor)
                        .wrapContentHeight(Alignment.CenterVertically)
                )
            }

            BalloonQuestionMark(Str(R.string.tap_text_to_edit_color))
        }
    }
}
@Composable
fun HueBar(
    currentHue: Float,
    setHue: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val pressOffset = remember {
        mutableStateOf(Offset.Zero)
    }
    val barSize = remember { mutableStateOf(Size.Zero) }

    Canvas(
        modifier = Modifier
            .height(40.dp)
            .width(AREA_WIDTH.dp)
            .clip(RoundedCornerShape(50))
            .emitDragGesture(interactionSource)
            .onSizeChanged {
                barSize.value = Size(it.width.toFloat(), it.height.toFloat())
            }
    ) {
        val drawScopeSize = size
        val bitmap = createBitmap(size.width.toInt(), size.height.toInt())
        val hueCanvas = Canvas(bitmap)

        val huePanel = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        val hueColors = IntArray((huePanel.width()).toInt())
        var hue = 0f
        for (i in hueColors.indices) {
            hueColors[i] = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))
            hue += 360f / hueColors.size
        }

        val linePaint = Paint()
        linePaint.strokeWidth = 0f
        for (i in hueColors.indices) {
            linePaint.color = hueColors[i]
            hueCanvas.drawLine(i.toFloat(), 0f, i.toFloat(), huePanel.bottom, linePaint)
        }

        drawBitmap(
            bitmap = bitmap,
            panel = huePanel
        )

        fun pointToHue(pointX: Float): Float {
            val width = huePanel.width()
            val x = when {
                pointX < huePanel.left -> 0f
                pointX > huePanel.right -> width
                else -> pointX - huePanel.left
            }
            return x * 360f / width
        }

        scope.collectForPress(interactionSource) { pressPosition ->
            val pressPos = pressPosition.x.coerceIn(0f..drawScopeSize.width)
            pressOffset.value = Offset(pressPos, size.height / 2)
            val selectedHue = pointToHue(pressPos)
            setHue(selectedHue)
        }

        drawCircle(
            Color.White,
            radius = size.height / 2,
            center = Offset(pressOffset.value.x, size.height / 2),
            style = Stroke(
                width = 2.dp.toPx()
            )
        )
    }

    LaunchedEffect(currentHue, barSize.value) {
        if (barSize.value != Size.Zero) {
            val x = (currentHue / 360f) * barSize.value.width
            pressOffset.value = Offset(x, barSize.value.height / 2)
        }
    }
}

@Composable
fun SatValPanel(
    hue: Float,
    currentSat: Float,
    currentVal: Float,
    setSatVal: (Float, Float) -> Unit
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val scope = rememberCoroutineScope()
    val pressOffset = remember {
        mutableStateOf(Offset.Zero)
    }
    val panelSize = remember { mutableStateOf(Size.Zero) }

    Canvas(
        modifier = Modifier
            .size(AREA_WIDTH.dp)
            .emitDragGesture(interactionSource)
            .clip(RoundedCornerShape(6.dp))
            .onSizeChanged {
                panelSize.value = Size(it.width.toFloat(), it.height.toFloat())
            }
    ) {
        val cornerRadius = 12.dp.toPx()
        val satValSize = size

        val bitmap = createBitmap(size.width.toInt(), size.height.toInt())
        val canvas = Canvas(bitmap)
        val satValPanel = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())

        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, 1f, 1f))

        val satShader = LinearGradient(
            satValPanel.left, satValPanel.top, satValPanel.right, satValPanel.top,
            -0x1, rgb, Shader.TileMode.CLAMP
        )
        val valShader = LinearGradient(
            satValPanel.left, satValPanel.top, satValPanel.left, satValPanel.bottom,
            -0x1, -0x1000000, Shader.TileMode.CLAMP
        )

        canvas.drawRoundRect(
            satValPanel,
            cornerRadius,
            cornerRadius,
            Paint().apply {
                shader = ComposeShader(
                    valShader,
                    satShader,
                    PorterDuff.Mode.MULTIPLY
                )
            }
        )

        drawBitmap(
            bitmap = bitmap,
            panel = satValPanel
        )

        fun pointToSatVal(pointX: Float, pointY: Float): Pair<Float, Float> {
            val width = satValPanel.width()
            val height = satValPanel.height()

            val x = when {
                pointX < satValPanel.left -> 0f
                pointX > satValPanel.right -> width
                else -> pointX - satValPanel.left
            }

            val y = when {
                pointY < satValPanel.top -> 0f
                pointY > satValPanel.bottom -> height
                else -> pointY - satValPanel.top
            }

            val satPoint = 1f / width * x
            val valuePoint = 1f - 1f / height * y

            return satPoint to valuePoint
        }

        scope.collectForPress(interactionSource) { pressPosition ->
            val pressPositionOffset = Offset(
                pressPosition.x.coerceIn(0f..satValSize.width),
                pressPosition.y.coerceIn(0f..satValSize.height)
            )

            pressOffset.value = pressPositionOffset
            val (satPoint, valuePoint) = pointToSatVal(pressPositionOffset.x, pressPositionOffset.y)
            setSatVal(satPoint, valuePoint)
        }

        drawCircle(
            color = Color.White,
            radius = 8.dp.toPx(),
            center = pressOffset.value,
            style = Stroke(
                width = 2.dp.toPx()
            )
        )

        drawCircle(
            color = Color.White,
            radius = 2.dp.toPx(),
            center = pressOffset.value,
        )
    }

    LaunchedEffect(currentSat, currentVal, panelSize.value) {
        if (panelSize.value != Size.Zero) {
            val x = currentSat * panelSize.value.width
            val y = (1f - currentVal) * panelSize.value.height
            pressOffset.value = Offset(x, y)
        }
    }
}