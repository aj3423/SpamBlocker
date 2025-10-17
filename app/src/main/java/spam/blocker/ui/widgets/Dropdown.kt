package spam.blocker.ui.widgets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val leadingIcon: (@Composable () -> Unit)? = null,
    val trailingIcon: (@Composable () -> Unit)? = null,
    val tooltip: String? = null,
    val selected: Boolean = false,
    val dismissOnClick: Boolean = true,
    val onLongClick: Lambda1<MutableState<Boolean>>? = null, // param: menuExpandedState
    val onClick: Lambda1<MutableState<Boolean>>? = null, // param: menuExpandedState
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        RowVCenterSpaced(
            space = 10,
            modifier =  M
                .combinedClickable(
                    onClick = {
                        onClick?.let {
                            it(menuExpandedState)
                        }
                        if (dismissOnClick) {
                            menuExpandedState.value = false
                        }
                    },
                    onLongClick = {

                        onLongClick?.let {
                            it(menuExpandedState)
                        }
                    }
                )
        ) {
            leadingIcon?.let {
                it()
            }

            RowVCenterSpaced(10, modifier = M.weight(1f)) {
                GreyLabel(
                    text = label,
                    modifier = M.weight(1f),
                    fontWeight = if (selected) FontWeight.Bold else null,
                )
                trailingIcon?.let { it() }
            }

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
    val trailingIcon: (@Composable ()->Unit)? = null,
    val enabled: Boolean = true,
    private val onCheckChange: Lambda1<Boolean>
) : IMenuItem {
    @Composable
    override fun Compose(menuExpandedState: MutableState<Boolean>) {
        val C = LocalPalette.current
        CheckBox(
            label = {
                RowVCenterSpaced(2) {
                    GreyLabel(
                        label,
                        color = if (enabled) C.textGrey else C.disabled
                    )
                    trailingIcon?.let {
                        it()
                    }
                }
            },
            enabled = enabled,
            checked = state.value,
            onCheckChange = onCheckChange,
        )
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
    val scrollState = rememberScrollState()

    Column(
        modifier = M
            .heightIn(max = 500.dp) // Important for it to not crash (scroll unbounded issue)
        .verticalScroll(scrollState)
        .simpleVerticalScrollbar(
            scrollState,
            offsetX = -8,
            persistent = true
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
