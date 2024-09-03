package spam.blocker.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.ui.util.M
import spam.blocker.util.Lambda
import spam.blocker.util.Lambda1

interface IMenuItem {
    @Composable
    fun Compose(menuExpandedState: MutableState<Boolean>) // expanded State
}

@Immutable
class CustomItem(
    val content: @Composable () -> Unit
) : IMenuItem {

    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        content()
    }
}

@Immutable
class DividerItem(
    val color: Color,
    private val thickness: Int = 1,
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        HorizontalDivider(thickness = thickness.dp, color = color)
    }
}

@Immutable
class LabelItem(
    val id: String = "",
    val label: String,
    val selected: Boolean = false,
    private val dismissOnClick: Boolean = true, // collapse the dropdown menu when item is clicked
    val onClick: Lambda,
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        GreyLabel(
            text = label,
            modifier = M
                .fillMaxWidth()
                .clickable {
                    onClick()
                    if (dismissOnClick)
                        menuExpandedState.value = false
                },
            fontWeight = if (selected) FontWeight.Bold else null,
        )
    }
}

@Immutable
class CheckItem(
    val checked: Boolean,
    val label: String,
    private val onCheckChange: Lambda1<Boolean>
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        CheckBox(label = { GreyLabel(label) }, checked = checked, onCheckChange = onCheckChange)
    }
}

@Composable
fun DropdownWrapper(
    items: List<IMenuItem>,
    content: @Composable (MutableState<Boolean>) -> Unit, // Boolean == expanded or not
) {
    Box {
        val expanded = remember { mutableStateOf(false) }

        content(expanded)

        DropdownMenu(
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
        ) {
            DropdownMenuItems(
                items = items,
                expanded = expanded,
            )
        }
    }
}

@Composable
fun DropdownMenuItems(
    items: List<IMenuItem>,
    expanded: MutableState<Boolean>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        items.forEach { item ->
            RowVCenter(
                modifier = M
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                item.Compose(expanded)
            }
        }
    }
}
