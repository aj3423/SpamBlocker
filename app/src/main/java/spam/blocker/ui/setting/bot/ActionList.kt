package spam.blocker.ui.setting.bot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import sh.calvin.reorderable.ReorderableColumn
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.isNextChainable
import spam.blocker.service.bot.isPreviousChainable
import spam.blocker.ui.M
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.SwipeInfo

// Draw a green/red line above/below each action card,
// indicating whether it's chainable to the previous/next action.
fun DrawScope.link(
    color: Color,
    atTop: Boolean,
) {
    val x = size.width / 2
    val y1 = if (atTop) 0f else size.height
    val y2 = if (atTop) -10f else size.height + 10

    drawLine(
        color = color,
        start = Offset(x, y1),
        end = Offset(x, y2),
        strokeWidth = 8f
    )
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionList(
    actions: SnapshotStateList<IAction>,
) {
    val editTrigger = rememberSaveable { mutableStateOf(false) }

    var clickedIndex by rememberSaveable { mutableIntStateOf(0) }

    if (editTrigger.value) {
        EditActionDialog(trigger = editTrigger, actions = actions, index = clickedIndex)
    }

    ReorderableColumn(
        list = actions.toList(),
        modifier = M.nestedScroll(DisableNestedScrolling()),
        onSettle = { fromIndex, toIndex ->
            actions.apply {
                add(toIndex, removeAt(fromIndex))
            }
        },
        onMove = {},
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) { index, action, isDragging ->

        key(action.hashCode()) {
            LeftDeleteSwipeWrapper(
                left = SwipeInfo(
                    onSwipe = {
                        // 1. remove from list
                        actions.removeAt(index)

                        // 2. snackbar shows behind popup, it's a bit complex to implement the Undelete here..
                    }
                )
            ) {
                ActionCard(
                    action = action,
                    modifier = M
                        .drawBehind {
                            // Draw a green/red line above/below each action card,
                            //  indicating whether it's chainable to the previous/next action.
                            if (!isDragging) {
                                val prev: IAction? = if (index == 0) null else actions[index - 1]
                                val prevChainable = isPreviousChainable(action, prev)
                                if (prevChainable != null)
                                    link(if (prevChainable) Color.Green else Color.Red, true)

                                val next: IAction? =
                                    if (index == actions.size - 1) null else actions[index + 1]
                                val nextChainable = isNextChainable(action, next)
                                if (nextChainable != null)
                                    link(if (nextChainable) Color.Green else Color.Red, false)
                            }
                        }
                        .draggableHandle() // make the card draggable
                        .combinedClickable(
                            onClick = {
                                clickedIndex = index
                                editTrigger.value = true
                            }
                        )
                )
            }
        }
    }
}

