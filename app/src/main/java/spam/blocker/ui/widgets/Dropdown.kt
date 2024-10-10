package spam.blocker.ui.widgets

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.util.Lambda1

// The built-in DropdownMenuItem is twice height as it should be.

interface IMenuItem {
    @Composable
    fun Compose(menuExpandedState: MutableState<Boolean>) // expanded State
}

@Immutable
class CustomItem(
    val content: @Composable (MutableState<Boolean>) -> Unit,
) : IMenuItem {

    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        content(menuExpandedState)
    }
}

@Immutable
class DividerItem(
    private val thickness: Int = 1,
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        HorizontalDivider(thickness = thickness.dp, color = LocalPalette.current.disabled)
    }
}

@Immutable
class LabelItem(
    val id: String = "",
    val label: String,
    val icon: (@Composable () -> Unit)? = null,
    val tooltip: String? = null,
    val selected: Boolean = false,
    val dismissOnClick: Boolean = true,
    val onClick: Lambda1<MutableState<Boolean>>? = null, // param: menuExpandedState
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        RowVCenterSpaced(10) {
            icon?.let {
                it()
            }

            GreyLabel(
                text = label,
                modifier = M
                    .weight(1f)
                    .let { baseModifier->
                        if (onClick != null) {
                            baseModifier.clickable {
                                onClick!!(menuExpandedState)
                                if (dismissOnClick) {
                                    menuExpandedState.value = false
                                }
                            }
                        } else {
                            baseModifier
                        }
                    },
                fontWeight = if (selected) FontWeight.Bold else null,
            )
            tooltip?.let {
                BalloonQuestionMark(tooltip = tooltip)
            }
        }
    }
}

@Immutable
class CheckItem(
    val state: MutableState<Boolean>,
    val label: String,
    private val onCheckChange: Lambda1<Boolean>
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        CheckBox(label = { GreyLabel(label) }, checked = state.value, onCheckChange = onCheckChange)
    }
}

@Composable
fun DropdownWrapper(
    items: List<IMenuItem>,
    modifier: Modifier = Modifier,
    content: @Composable (MutableState<Boolean>) -> Unit, // Boolean == expanded or not
) {
    val C = LocalPalette.current

    Box {
        val expanded = remember { mutableStateOf(false) }

        content(expanded)

        DropdownMenu(
            modifier = modifier,
            expanded = expanded.value,
            onDismissRequest = { expanded.value = false },
            containerColor = C.menuBg,
            border = BorderStroke(1.dp, C.menuBorder)
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
