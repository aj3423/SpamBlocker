package spam.blocker.ui.widgets

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda
import spam.blocker.util.Lambda1
import spam.blocker.util.Util.inRange

const val BUTTON_CORNER_RADIUS = 4
const val BUTTON_H_PADDING = 12
const val BUTTON_H = 26

// The built-in Button is based on Surface, which has a minimal width as 48dp,
//  as: minimumInteractiveComponentSize
// So customize the Button to get rid of it.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Button(
    content: @Composable RowScope.() -> Unit,
    modifier: Modifier = M.height(BUTTON_H.dp),
    onLongClick: Lambda? = null,
    enabled: Boolean = true,
    borderColor: Color = LocalPalette.current.textGrey,
    shape: Shape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp),
    contentPadding: PaddingValues = PaddingValues(BUTTON_H_PADDING.dp, 0.dp),
    onClick: () -> Unit,
) {
    val C = LocalPalette.current

    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                color = if (enabled) borderColor else C.disabled,
                shape = shape
            )

            .combinedClickable(
                onClick = { if (enabled) onClick() },
                onLongClick = { if (enabled) onLongClick?.invoke() }
            ),
        propagateMinConstraints = true
    ) {
        RowCenter(
            Modifier.padding(contentPadding),
        ) {
            content()
        }
    }
}


@Composable
fun StrokeButton(
    label: String? = null,
    color: Color,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onLongClick: Lambda? = null,
    shape: RoundedCornerShape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp),
    contentPadding: PaddingValues = PaddingValues(BUTTON_H_PADDING.dp, 0.dp),
    enabled: Boolean = true,
    onClick: Lambda,
) {
    Button(
        modifier = modifier.height(BUTTON_H.dp),
        enabled = enabled,
        onLongClick = onLongClick,
        contentPadding = contentPadding,
        borderColor = color,
        shape = shape,
        onClick = onClick,
        content = {
            RowVCenterSpaced(4) {
                icon?.let { it() }
                label?.let {
                    Text(
                        text = label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )
}

@Composable
fun GreyButton(
    label: String,
    // optional
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: Lambda,
) {
    StrokeButton(
        label = label,
        color = LocalPalette.current.textGrey,
        modifier = modifier,
        enabled = enabled,
        onClick = onClick,
    )
}


@Composable
fun FooterButton(
    label: String? = null,
    color: Color = LocalPalette.current.textGrey,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,

    footerIconId: Int,
    footerSize: Int,
    footerColor: Color = color,
    footerOffset: Pair<Int, Int> = Pair(-3, -3),

    onLongClick: Lambda? = null,
    onClick: Lambda,
) {
    val C = LocalPalette.current

    Box(
        modifier = modifier.wrapContentSize()
    ) {
        StrokeButton(
            label = label,
            icon = icon,
            color = color,
            modifier = modifier,
            onClick = onClick,
            enabled = enabled,
            onLongClick = onLongClick,
        )

        Icon(
            imageVector = ImageVector.vectorResource(footerIconId),
            contentDescription = "",
            tint = if (enabled) footerColor else C.disabled,
            modifier = Modifier
                .size(footerSize.dp)
                .align(Alignment.BottomEnd)
                .offset(footerOffset.first.dp, footerOffset.second.dp)
        )
    }
}

@Composable
fun LongPressButton(
    label: String,
    color: Color,
    footerIconId: Int = R.drawable.ic_time_slot,
    onClick: Lambda,
    onLongClick: Lambda,
) {
    FooterButton(
        label = label,
        color = color,
        footerIconId = footerIconId,
        footerSize = 9,
        footerOffset = Pair(-3, -3),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

// Show icon only, label only, or both
enum class ComboDisplayType {
    Label, Icon, IconLabel,
}

// Button with a small triangle sign at the bottom right,
//  the label always follows the selected item
@Composable
fun ComboBox(
    items: List<LabelItem>,
    selected: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    displayType: ComboDisplayType = ComboDisplayType.Label, // show label/icon/both
    color: Color = LocalPalette.current.textGrey,
    warning: (@Composable () -> Unit)? = {
        ResIcon(R.drawable.ic_question_circle, color = DarkOrange, modifier = M.size(18.dp))
    },
    footerOffset: Pair<Int, Int> = Pair(-4, -4),
    footerSize: Int = 6,
    footerIconId: Int = R.drawable.ic_dropdown_footer,
    onLongClick: Lambda? = null,
    expander: Lambda1<MutableState<Boolean>> = { it.value = true },
) {
    DropdownWrapper(
        items = items,
    ) { expanded ->
        FooterButton(
            label = if (!inRange(selected, items))
                null
            else
                when (displayType) {
                    ComboDisplayType.Label, ComboDisplayType.IconLabel -> items[selected].label
                    else -> null
                },
            icon = if (!inRange(selected, items))
                warning
            else
                when (displayType) {
                    ComboDisplayType.Icon, ComboDisplayType.IconLabel -> {
                        {
                            items[selected].leadingIcon?.let { it() }
                        }
                    }
                    else -> null
                },
            color = color,
            modifier = modifier,
            enabled = enabled,
            onLongClick = onLongClick,
            footerOffset = footerOffset,
            footerSize = footerSize,
            footerIconId = footerIconId,
        ) {
            expander(expanded)
        }
    }
}


// Button with a small triangle sign at the bottom right,
//  has a fixed label.
@Composable
fun MenuButton(
    label: String,
    items: List<IMenuItem>,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textGrey,
) {
    DropdownWrapper(items = items) { expanded ->
        FooterButton(
            label = label,
            color = color,
            modifier = modifier,
            footerOffset = Pair(-4, -4),
            footerSize = 6,
            footerIconId = R.drawable.ic_dropdown_footer,
        ) {
            expanded.value = true
        }
    }
}


// Same as MenuButton but shows different menus for short/long tap.
@Composable
fun MenuButton(
    label: String,
    items: List<IMenuItem>,
    longTapItems: List<IMenuItem>,
    modifier: Modifier = Modifier,
    footerIconId: Int = R.drawable.ic_time_slot,
    color: Color = LocalPalette.current.textGrey,
) {
    DropdownWrapper(items = items) { expandedForTap ->
        DropdownWrapper(items = longTapItems) { expandedForLongTap ->
            FooterButton(
                label = label,
                color = color,
                modifier = modifier,
                footerOffset = Pair(-3, -3),
                footerSize = 9,
                footerIconId = footerIconId,
                onClick = {
                    expandedForTap.value = true
                },
                onLongClick = {
                    expandedForLongTap.value = true
                }
            )
        }
    }
}