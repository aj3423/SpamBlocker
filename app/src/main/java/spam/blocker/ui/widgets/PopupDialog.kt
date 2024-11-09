package spam.blocker.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import spam.blocker.ui.M
import spam.blocker.ui.screenWidthDp
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda

const val PopupPaddingHorizontal = 16
const val PopupPaddingVertical = 16
const val PopupScrollVPadding = 4

data class PopupSize(
    val percentage: Float = 0.9f,
    val minWidth: Int = 300,
    val maxWidth: Int = 600
) {
    @Composable
    fun calculate(): Int {
        var width = (screenWidthDp() * percentage).toInt()

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
    scrollEnabled: Boolean = true,
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
            val C = LocalPalette.current
            val scrollState = rememberScrollState()

            Card(
                modifier = M
                    .width(
                        popupSize?.calculate()?.dp ?: Dp.Unspecified
                    ),

                border = BorderStroke(1.dp, color= C.dialogBorder),
                colors = CardDefaults.cardColors(
                    containerColor = LocalPalette.current.dialogBg,
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

