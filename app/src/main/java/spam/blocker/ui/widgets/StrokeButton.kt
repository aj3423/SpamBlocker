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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda


// The built-in Button is based on Surface, which has a minimal width as 48dp,
//  as: minimumInteractiveComponentSize
// So customize the Button to get rid of it.
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: Lambda? = null,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .border(
                width = 2.dp,
                color = color,
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
    label: String,
    color: Color,

    modifier: Modifier = Modifier,
    onLongClick: Lambda? = null,
    shape: RoundedCornerShape = RoundedCornerShape(4.dp),
    contentPadding: PaddingValues = PaddingValues(12.dp, 0.dp),
    enabled: Boolean = true,
    onClick: Lambda,
) {
    Button(
        modifier = modifier.height(26.dp),
        enabled = enabled,
        onLongClick = onLongClick,
        contentPadding = contentPadding,
        color = color,
        shape = shape,
        onClick = onClick
    ) {
        Text(
            text = label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun GreyButton(
    label: String,
    // optional
    modifier: Modifier = Modifier,
    onClick: Lambda,
) {
    StrokeButton(
        label = label,
        color = LocalPalette.current.textGrey,
        modifier = modifier,
        onClick = onClick,
    )
}


@Composable
fun FooterButton(
    label: String, color: Color, footerIconId: Int,
    modifier: Modifier = Modifier,
    footerSize: Int,
    footerOffset: Pair<Int, Int>,
    onLongClick: Lambda? = null,
    onClick: Lambda,
) {
    Box(
        modifier = modifier.wrapContentSize()
    ) {
        StrokeButton(
            label = label,
            color = color,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
        )
        Icon(
            imageVector = ImageVector.vectorResource(footerIconId),
            contentDescription = "",
            tint = color,
            modifier = Modifier
                .size(footerSize.dp)
                .align(Alignment.BottomEnd)
                .offset(footerOffset.first.dp, footerOffset.second.dp)
        )
    }
}

@Composable
fun LongPressButton(
    label: String, color: Color,
    onClick: Lambda,
    onLongClick: Lambda,
) {
    FooterButton(
        label = label, color = color, footerIconId = R.drawable.ic_time_slot,
        footerSize = 9, footerOffset = Pair(-3, -3),
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

// Button with a small triangle sign at the bottom right,
//  the label always follows the selected item
@Composable
fun Spinner(
    items: List<LabelItem>,
    selected: Int,
    modifier: Modifier = Modifier,
    color: Color = LocalPalette.current.textGrey,
) {
    DropdownWrapper(
        items = items,
    ) { expanded ->
        FooterButton(
            label = items[selected].label,
            color = color,
            modifier = modifier,
            footerOffset = Pair(-4, -4),
            footerSize = 6,
            footerIconId = R.drawable.spinner_arrow,
        ) {
            expanded.value = true
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
            footerIconId = R.drawable.spinner_arrow,
        ) {
            expanded.value = true
        }
    }
}

