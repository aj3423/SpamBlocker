package spam.blocker.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.util.Lambda
import kotlin.math.roundToInt

const val PopupPaddingHorizontal = 16
const val PopupPaddingVertical = 16

data class PopupSize(
    val percentage: Float = 0.9f,
    val minWidth: Int = 300,
    val maxWidth: Int = 600
) {
    @Composable
    fun calculate(): Int {
        val screenWidth = LocalContext.current.resources.displayMetrics.widthPixels

        // Calculate x% of the screen width
        var width = with(LocalDensity.current) {
            (screenWidth * percentage).roundToInt().toDp().value.toInt()
        }
        // must >= minWidth
        if (width < minWidth) {
            width = minWidth
        }
        // must <= maxWidth
        if (width > maxWidth) {
            width = maxWidth
        }

        return width
    }
}


@Composable
fun PopupDialog(
    trigger: MutableState<Boolean>,
    title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onDismiss: Lambda? = null,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    popupSize: PopupSize? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (trigger.value) {

        Dialog(
            onDismissRequest = {
                trigger.value = false
                onDismiss?.invoke()
            },

            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = popupSize == null,
            )
        ) {
            val scrollState = rememberScrollState()

            Card(
                modifier = M
                    .width(
                        popupSize?.calculate()?.dp ?: Dp.Unspecified
                    )
                    .verticalScroll(scrollState)
                    .verticalScrollbar(
                        scrollState,
                        offsetX = -8,
                        persistent = true,
                        scrollBarColor = SkyBlue
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = LocalPalette.current.dialogBg,
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = M
                        .padding(
                            horizontal = PopupPaddingHorizontal.dp,
                            vertical = PopupPaddingVertical.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Icon
                    icon?.let {
                        icon()
                    }

                    // Title
                    title?.let {
                        title()
                    }

                    // Content
                    content()

                    // Button Row
                    buttons?.let {
                        RowVCenter(
                            horizontalArrangement = Arrangement.End,
                            modifier = M
                                .padding(top = 16.dp)
                                .align(Alignment.End)
                        ) {
                            buttons()
                        }
                    }
                }
            }
        }
    }
}

// A PopupDialog with a bottom button row, with customizable buttons.
@Composable
fun ConfirmDialog(
    trigger: MutableState<Boolean>,
    title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    dismiss: Lambda? = null,
    popupSize: PopupSize? = null,
    negative: (@Composable () -> Unit)? = null,
    positive: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    PopupDialog(
        trigger = trigger,
        content = content,
        popupSize = popupSize,
        title = title,
        icon = icon,
        onDismiss = dismiss,
        buttons = {
            negative?.invoke()

            Spacer(modifier = M.width(10.dp))

            positive?.invoke()
        }
    )
}
