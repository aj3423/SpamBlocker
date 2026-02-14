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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toRect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.Teal200
import spam.blocker.util.Lambda
import spam.blocker.util.Lambda1
import android.graphics.Color as AndroidColor

private const val AREA_WIDTH = 260 // .dp

@Composable
fun ColorButton(
    color: Int?,
    defaultText: String? = null,
    enabled: Boolean = true,
    onClick: Lambda
) {
    StrokeButton(
        label = if (color == null) defaultText else null,
        color = LocalPalette.current.textGrey,
        enabled = enabled,
        contentPadding = PaddingValues(
            horizontal = if (color == null) BUTTON_H_PADDING.dp else 0.dp, vertical = 0.dp
        ),
        icon = if (color == null) {
            null
        } else {
            {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(60.dp)
                        .clip(RoundedCornerShape(BUTTON_CORNER_RADIUS.dp))
                        .background(Color(color))
                )
            }
        },
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
        5,6 -> (W / 3 to H / 2)
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
                            modifier = M.background(Color(it))
                                // It doesn't appear on some devices without the -0.01,
                                //  seems it just has to be smaller than ... whatever.
                                .width((w-0.001).dp)
                                .height((h-0.001).dp)
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
    color: Int?,
    defaultText: String? = null,
    defaultColor: Int = Color.White.toArgb(),
    clearable: Boolean = false,
    enabled: Boolean = true,
    onSelect: Lambda1<Int?>
) {
    val trigger = remember { mutableStateOf(false) }

    ColorPickerPopup(
        trigger = trigger,
        initColor = color ?: defaultColor,
        clearable = clearable,
        onSelect = onSelect,
    )

    ColorButton(
        color = color,
        defaultText = defaultText,
        enabled = enabled,
    ) {
        trigger.value = true
    }
}


@Composable
fun ColorPickerPopup(
    trigger: MutableState<Boolean>,
    initColor: Int,
    clearable: Boolean = false,
    onSelect: Lambda1<Int?>,
) {
    if (!trigger.value)
        return

    var rgb by remember {
        mutableIntStateOf((initColor and 0xffffff))
    }
    var a by remember {
        mutableIntStateOf((initColor shr 24).toInt())
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            RowVCenterSpaced(8) {
                if (clearable) {

                    StrokeButton(
                        label = Str(R.string.clear),
                        color = Salmon
                    ) {
                        onSelect(null)
                        trigger.value = false
                    }
                }
                StrokeButton(
                    label = Str(R.string.save),
                    color = Teal200
                ) {
                    onSelect((a shl 24) + rgb)
                    trigger.value = false
                }
            }
        }
    ) {
        var argbColor by remember(rgb, a) {
            mutableStateOf(Color(rgb.toLong() + (a.toLong() shl 24)))
        }

        var colorString by remember(argbColor) {
            mutableStateOf(String.format("%08X", argbColor.toArgb()))
        }
        LaunchedEffect(colorString) {
            val parsed = colorString.parseColorString()
            if (parsed != null) {
                a = parsed.first
                rgb = parsed.second
            }
        }

        val hsv by remember(rgb) {
            val hsvArray = floatArrayOf(0f, 0f, 0f)
            AndroidColor.colorToHSV(rgb, hsvArray)
            mutableStateOf(
                Triple(hsvArray[0], hsvArray[1], hsvArray[2])
            )
        }

        // Top panel
        SatValPanel(hue = hsv.first) { sat, value ->
            val newRgb = AndroidColor.HSVToColor(floatArrayOf(hsv.first, sat, value))
            rgb = newRgb and 0xffffff
        }

        // Middle bar
        HueBar { hue ->
            val newRgb = AndroidColor.HSVToColor(floatArrayOf(hue, hsv.second, hsv.third))
            rgb = newRgb and 0xffffff
        }

        // Bottom preview area
        RowVCenterSpaced(2) {
            Spacer(modifier = M.width(32.dp)) // make the Box below horizontally centered...

            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(argbColor),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    value = colorString,
                    onValueChange = {
                        colorString = it
                    },
                    textStyle = TextStyle(
                        textAlign = TextAlign.Center,
                        fontSize = 18.sp,
                        color = argbColor.contrastColor()
                    ),

                    modifier = Modifier
                        .height(200.dp)
                        .background(argbColor)
                        .wrapContentHeight(align = Alignment.CenterVertically)
                )
            }

            BalloonQuestionMark(Str(R.string.tap_text_to_edit_color))
        }
    }
}

fun String.parseColorString(): Pair<Int, Int>? {
    if (this.length != 8) {
        return null
    }

    val alpha = this.substring(0, 2).toIntOrNull(16)
    if (alpha == null)
        return null

    val rgb = this.substring(2, 8).toIntOrNull(16)
    if (rgb == null)
        return null

    return Pair(alpha, rgb)
}

fun Color.contrastColor(): Color {
    // Calculate the perceptive luminance (aka luma) - human eye favors green color...
    val luma = (0.299 * red) + (0.587 * green) + (0.114 * blue)

    // Return black for bright colors, white for dark colors
    return if (luma > 0.6) {
        Color.Black
    } else {
        Color.White
    }
}

@Composable
fun HueBar(
    setColor: (Float) -> Unit
) {
    val scope = rememberCoroutineScope()
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val pressOffset = remember {
        mutableStateOf(Offset.Zero)
    }

    Canvas(
        modifier = Modifier
            .height(40.dp)
            .width(AREA_WIDTH.dp)
            .clip(RoundedCornerShape(50))
            .emitDragGesture(interactionSource)
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
            setColor(selectedHue)
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
}

fun CoroutineScope.collectForPress(
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
fun SatValPanel(
    hue: Float,
    setSatVal: (Float, Float) -> Unit
) {
    val interactionSource = remember {
        MutableInteractionSource()
    }
    val scope = rememberCoroutineScope()
    val pressOffset = remember {
        mutableStateOf(Offset.Zero)
    }

    Canvas(
        modifier = Modifier
            .size(AREA_WIDTH.dp)
            .emitDragGesture(interactionSource)
            .clip(RoundedCornerShape(6.dp))
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
}
