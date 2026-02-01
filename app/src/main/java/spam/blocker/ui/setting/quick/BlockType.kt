package spam.blocker.ui.setting.quick

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.def.Def.DEFAULT_HANG_UP_DELAY
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreyIcon20
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.spf

@Composable
fun ConfigHangUp(
    trigger: MutableState<Boolean>,
    delay: MutableIntState,
) {
    PopupDialog(
        trigger = trigger,
        content = {
            NumberInputBox(
                intValue = delay.intValue,
                onValueChange = { newValue, hasError ->
                    if (!hasError && newValue != null) {
                        delay.intValue = newValue
                    }
                },
                labelId = R.string.delay,
                leadingIconId = R.drawable.ic_duration,
                helpTooltipId = R.string.help_hang_up_delay
            )
        })
}

@Composable
fun BlockType() {
    val ctx = LocalContext.current
    val spf = spf.BlockType(ctx)

    val selected = remember {
        mutableIntStateOf(spf.type)
    }

    val options = remember {
        val icons = listOf<@Composable () -> Unit>(
            // list.map{} doesn't support returning @Composable...
            { GreyIcon20(iconId = R.drawable.ic_call_blocked) },
            { GreyIcon20(iconId = R.drawable.ic_call_miss) },
            { GreyIcon20(iconId = R.drawable.ic_hang) },
        )
        ctx.resources.getStringArray(R.array.block_type_list).mapIndexed { index, label ->
            LabelItem(
                label = label,
                leadingIcon = icons[index],
                onClick = {
                    when (index) {
                        0, 1 -> { // Reject, Silence
                            spf.type = index
                            selected.intValue = index
                        }

                        2 -> { // Hang Up

                            G.permissionChain.ask(
                                ctx,
                                listOf(
                                    PermissionWrapper(Permission.phoneState),
                                    PermissionWrapper(Permission.callLog),
                                    PermissionWrapper(Permission.answerCalls)
                                )
                            ) { granted ->
                                if (granted) {
                                    spf.type = index
                                    selected.intValue = index
                                } else {
                                    selected.intValue = spf.type
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
        helpTooltip = Str(R.string.help_block_type),
        content = {
            val C = LocalPalette.current
            RowVCenterSpaced(4) {

                if (selected.intValue == Def.BLOCK_TYPE_ANSWER_AND_HANGUP) {
                    val delay = remember {
                        mutableIntStateOf(spf.delay.toIntOrNull() ?: DEFAULT_HANG_UP_DELAY)
                    }
                    LaunchedEffect(delay.intValue) {
                        spf.delay = delay.intValue.toString()
                    }

                    val popupTrigger = rememberSaveable { mutableStateOf(false) }
                    ConfigHangUp(popupTrigger, delay)

                    StrokeButton(
                        label = "${delay.intValue} ${Str(R.string.seconds_short)}",
                        color = C.textGrey,
                    ) {
                        popupTrigger.value = true
                    }
                }

                ComboBox(
                    options,
                    selected.intValue,
                )
            }
        }
    )
}