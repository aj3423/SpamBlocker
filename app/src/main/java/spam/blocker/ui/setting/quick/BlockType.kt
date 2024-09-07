package spam.blocker.ui.setting.quick

import android.Manifest
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.Spinner
import spam.blocker.util.Permission
import spam.blocker.util.PermissionChain
import spam.blocker.util.SharedPref.BlockType

@Composable
fun BlockType() {
    val ctx = LocalContext.current
    val spf = BlockType(ctx)

    val permChain = remember {
        PermissionChain(
            ctx,
            listOf(
                Permission(Manifest.permission.READ_PHONE_STATE),
                Permission(Manifest.permission.READ_CALL_LOG),
                Permission(Manifest.permission.ANSWER_PHONE_CALLS)
            )
        )
    }
    permChain.Compose()

    val selected = remember {
        mutableIntStateOf(spf.getType())
    }

    val options = remember {
        val icons = listOf<@Composable ()-> Unit>( // list.map{} doesn't support returning @Composable...
            { GreyIcon(iconId = R.drawable.ic_call_blocked, modifier = M.size(16.dp)) },
            { GreyIcon(iconId = R.drawable.ic_call_miss, modifier = M.size(16.dp)) },
            { GreyIcon(iconId = R.drawable.ic_hang, modifier = M.size(16.dp)) },
        )
        ctx.resources.getStringArray(R.array.block_type_list).mapIndexed { index, label ->
            LabelItem(
                label = label,
                icon = icons[index],
                onClick = {
                    when(index) {
                        0, 1 -> { // Reject, Silence
                            spf.setType(index)
                            selected.intValue = index
                        }
                        2 -> { // Answer+Hangup
                            permChain.ask { granted ->
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