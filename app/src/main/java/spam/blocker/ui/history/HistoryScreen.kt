package spam.blocker.ui.history

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.FabWrapper
import spam.blocker.ui.widgets.Str
import spam.blocker.util.Permissions
import spam.blocker.util.Util
import spam.blocker.R
import spam.blocker.util.NormalPermission
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
                Def.ForNumber -> Permissions.launcherSetAsCallScreeningApp(null)
                Def.ForSms -> {
                    val smsEnabled = spf.Global(ctx).isSmsEnabled()

                    // Show a warning at the top if SMS is enabled but no permission.
                    var enabledButNoPermission by remember {
                        mutableStateOf(
                            smsEnabled && !Permissions.isReceiveSmsPermissionGranted(
                                ctx
                            )
                        )
                    }
                    if (smsEnabled) {
                        G.permissionChain.ask(
                            ctx,
                            listOf(
                                NormalPermission(Manifest.permission.RECEIVE_SMS),
                            )
                        ) { granted ->
                            enabledButNoPermission = !granted
                        }
                    }

                    if (enabledButNoPermission) {
                        Text(
                            text = Str(R.string.no_sms_receive_permission),
                            color = DarkOrange,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = M.padding(10.dp, 16.dp, 10.dp, 4.dp)
                        )
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
