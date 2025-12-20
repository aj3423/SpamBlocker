package spam.blocker.ui.widgets

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.ui.M
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.SimCard


@Composable
fun SimCardIcon(
    slotIndex: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.wrapContentSize()
    ) {
        val color = Color(("$slotIndex dummy salt....").hashCode().toLong() or 0xff202020)
        ResIcon(
            iconId = R.drawable.ic_sim_card,
            color = color,
            modifier = M.size(22.dp)
        )

        Text(
            text = "${slotIndex+1}",
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.offset(x = 7.5.dp)
        )
    }
}

@Composable
fun SimPicker(
    simSlot: MutableState<Int?>
) {
    val ctx = LocalContext.current

    RowVCenterSpaced(8) {
        var allCards by remember { mutableStateOf(SimCard.listSimCards(ctx)) }
        var simRefreshed by remember { mutableStateOf(true) }

        var selected by remember(simSlot.value, simRefreshed) {
            mutableIntStateOf(
                if (simSlot.value == null) {
                    0
                } else {
                    val idx = allCards.indexOfFirst { it.slotIndex == simSlot.value }
                    if (idx == -1) 0 else idx + 1 // +1 for the first item "Any"
                }
            )
        }
        val menuItems = remember(simRefreshed) {
            val ret = mutableListOf<LabelItem>()

            // 1. "Any", it doesn't require Permission.phoneState
            ret += LabelItem(
                label = ctx.getString(R.string.any)
            ) {
                simSlot.value = null
            }
            // 2. all SIM slots
            ret += allCards.map { sim ->
                LabelItem(
                    leadingIcon = { SimCardIcon(sim.slotIndex) },
                    label = ctx.getString(R.string.sim_slot_template).format("${sim.slotIndex + 1}")
                ) {
                    G.permissionChain.ask(
                        ctx,
                        listOf(PermissionWrapper(Permission.phoneState))
                    ) { granted ->
                        if (granted) {
                            simSlot.value = sim.slotIndex
                        }
                    }
                }
            }
            ret
        }
        ComboBox(
            items = menuItems,
            selected = selected,
            enabled = Build.VERSION.SDK_INT >= ANDROID_12,
            displayType = ComboDisplayType.IconLabel,
            expander = { dropdownExpanded ->
                G.permissionChain.ask(
                    ctx,
                    listOf(PermissionWrapper(Permission.phoneState))
                ) { granted ->
                    if (granted) {
                        allCards = SimCard.listSimCards(ctx)
                        simRefreshed = !simRefreshed
                        dropdownExpanded.value = true
                    }
                }
            }
        )
    }
}