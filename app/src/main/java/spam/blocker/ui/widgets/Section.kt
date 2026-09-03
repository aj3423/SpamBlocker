package spam.blocker.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.slightDiff

@Composable
fun Section(
    title: String?,
    horizontalPadding : Int = 0,
    bgColor: Color = G.palette.background,
    isCollapsed: MutableState<Boolean>? = null, // null == non-foldable, e.g. sections in api dialogs
    onToggleCollapse: ((Boolean) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = M
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        // the rectangle section border
        Box(
            modifier = M
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding.dp)
                .border(0.5.dp, bgColor.slightDiff(), shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .wrapContentHeight()
        ) {
            AnimatedVisibleV(isCollapsed?.value != true) {
                content()
            }
        }

        // the section title
        if (title != null) {
            Box(
                modifier = M
                    .fillMaxWidth()
                    .offset(y = (-8).dp)
                    .let {
                        if (isCollapsed != null) { // is foldable
                            it.clickable {
                                isCollapsed.value = !isCollapsed.value
                                onToggleCollapse?.invoke(isCollapsed.value)
                            }
                        } else it
                    }
            ) {
                Box(
                    modifier = M
                        .wrapContentWidth()
                        .offset(x = 20.dp)
                        .background(bgColor)
                ) {
                    RowVCenterSpaced(4, modifier = Modifier.padding(horizontal = 10.dp)) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            color = G.palette.textGrey,
                            lineHeight = 13.sp,
                        )
                        if (isCollapsed?.value == true) {
                            GreyIcon16(
                                iconId = R.drawable.ic_dropdown_arrow,
                            )
                        }
                    }
                }
            }
        }
    }
}