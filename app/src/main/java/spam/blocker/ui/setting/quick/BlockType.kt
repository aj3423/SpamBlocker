package spam.blocker.ui.setting.quick

import android.Manifest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Spinner
import spam.blocker.util.NormalPermission
import spam.blocker.util.spf

@Composable
fun BlockType() {
    val ctx = LocalContext.current
    val spf = spf.BlockType(ctx)

    val selected = remember {
        mutableIntStateOf(spf.getType())
    }

    val options = remember {
        val icons = listOf<@Composable () -> Unit>(
            // list.map{} doesn't support returning @Composable...
            { GreyIcon16(iconId = R.drawable.ic_call_blocked) },
            { GreyIcon16(iconId = R.drawable.ic_call_miss) },
            { GreyIcon16(iconId = R.drawable.ic_hang) },
        )
        ctx.resources.getStringArray(R.array.block_type_list).mapIndexed { index, label ->
            LabelItem(
                label = label,
                icon = icons[index],
                onClick = {
                    when (index) {
                        0, 1 -> { // Reject, Silence
                            spf.setType(index)
                            selected.intValue = index
                        }

                        2 -> { // Answer+Hangup

                            G.permissionChain.ask(
                                ctx,
                                listOf(
                                    NormalPermission(Manifest.permission.READ_PHONE_STATE),
                                    NormalPermission(Manifest.permission.READ_CALL_LOG),
                                    NormalPermission(Manifest.permission.ANSWER_PHONE_CALLS)
                                )
                            ) { granted ->
                                if (granted) {
                                    spf.setType(index)
                                    selected.intValue = index
                                } else {
                                    selected.intValue = spf.getType()
                                }
                            }
                        }
                    }
                }
            )
        }
    }


    LabeledRow(
        R.string.block_type,
        helpTooltipId = R.string.help_block_type,
        content = {

            Spinner(
                options,
                selected.intValue,
            )
        }
    )
}