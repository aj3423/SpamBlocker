package spam.blocker.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.def.Def
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf


@Composable
fun HistoryScreen(
    vm: HistoryViewModel,
) {
    val ctx = LocalContext.current

    // Hide FAB on scrolling to the last item
    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf {
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val lastItemIndex = listState.layoutInfo.totalItemsCount - 1
            lastVisibleIndex < lastItemIndex || listState.firstVisibleItemIndex == 0
        }
    }

    FabWrapper(
        fabRow = { positionModifier ->
            // Two FABs
            HistoryFabs(
                visible = fabVisible,
                vm = vm,
                modifier = positionModifier,
            )
        }
    ) {
        Column {
            when (vm.forType) {
                Def.ForNumber -> {
                    G.permissionChain.ask(
                        ctx,
                        listOf(
                            PermissionWrapper(Permission.callScreening),
                        )
                    ) { granted ->
                    }
                }
                Def.ForSms -> {
                    val smsEnabled = spf.Global(ctx).isSmsEnabled()

                    if (smsEnabled) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                PermissionWrapper(Permission.receiveSMS),
                            )
                        ) { granted ->
                        }
                    }
                }
            }

            HistoryList(
                lazyState = listState,
                vm = vm,
            )
        }
    }
}
