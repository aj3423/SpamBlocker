package spam.blocker.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import spam.blocker.G
import spam.blocker.ui.M
import spam.blocker.ui.lighten
import spam.blocker.ui.maxScreenHeight
import spam.blocker.ui.screenWidthDp
import spam.blocker.util.Lambda
import kotlin.math.min

const val PopupPaddingHorizontal = 16
const val PopupPaddingVertical = 16
const val PopupScrollVPadding = 4

data class PopupSize(
    val minWidthDp: Int = 300,
    val maxWidthDp: Int = 600,
    val maxWidthPercentage: Float = 0.9f,
) {
    fun minWidth(): Float {
        return minWidthDp.toFloat()
    }
    fun maxWidth(screenWidthDp: Float): Float {
        return min(
            maxWidthDp.toFloat(),
            (screenWidthDp * maxWidthPercentage)
        )
    }
}


@Composable
fun PopupDialog(
    trigger: MutableState<Boolean>,
    title: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    onDismiss: Lambda? = null,
    manuallyDismissable: Boolean = true,
    buttons: (@Composable RowScope.() -> Unit)? = null,
    popupSize: PopupSize? = null,
    scrollEnabled: Boolean = true,
    transparentBackground: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (trigger.value) {

        Dialog(
            onDismissRequest = {
                if (manuallyDismissable) {
                    trigger.value = false
                }
                onDismiss?.invoke()
            },

            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = popupSize == null,
            )
        ) {
            // Disable the dialog background dim effect.
            // Enable this when taking screenshots..
            if (transparentBackground)
                (LocalView.current.parent as DialogWindowProvider).window.setDimAmount(0.0f)

            val C = G.palette
            val scrollState = rememberScrollState()

            Card(
                modifier = M
                    .maxScreenHeight(0.9f)
                    .wrapContentWidth()
                    .widthIn(
                        min = popupSize?.minWidth()?.dp ?: Dp.Unspecified,
                        max = popupSize?.maxWidth(screenWidthDp())?.dp ?: Dp.Unspecified,
                    ),

                border = BorderStroke(1.dp, color= C.dialogBg.lighten(0.1f)),
                colors = CardDefaults.cardColors(
                    containerColor = G.palette.dialogBg,
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                Column(
                    modifier = M
                        .padding(
                            vertical = PopupScrollVPadding.dp
                        )
                        .then(
                            if (!scrollEnabled) M else {
                                M
                                    .verticalScroll(scrollState)
                                    .simpleVerticalScrollbar(
                                        scrollState,
                                        offsetX = -8,
                                        persistent = true
                                    )
                            }
                        )
                        .padding(
                            horizontal = PopupPaddingHorizontal.dp,
                            vertical = (PopupPaddingVertical- PopupScrollVPadding).dp
                        )
                    ,
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

