package spam.blocker.ui.setting.regex

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.retain.retain
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.RegexRule
import spam.blocker.def.Def.ForNumber
import spam.blocker.def.Def.ForSms
import spam.blocker.ui.setting.regex.RegexMode.ModeType
import spam.blocker.ui.setting.regex.RegexMode.allNumberModes
import spam.blocker.ui.setting.regex.RegexMode.isForNumberRegexMode
import spam.blocker.ui.setting.regex.RegexMode.regexModeByType
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.IMenuItem
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Lambda1


@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun RegexLeadingDropdownIcon(
    initialMode: RegexMode.Base,
    regexModeState: MutableIntState,
) {
    val ctx = LocalContext.current
    val C = G.palette


    val dropdownItems = remember(Unit) {
        val modes = allNumberModes().filter {
            // hide "CNAP" for SMS/QuickCopy
            if (!initialMode.modeType.isForNumberRegexMode()) {
                it.modeType != ModeType.CallerName
            } else {
                true
            }
        }

        val items: MutableList<IMenuItem> = mutableListOf(
            LabelItem(
                label = "     "/*padding...*/ + ctx.getString(R.string.regex_mode),
            ),
            DividerItem(thickness = 1),
        )

        items += modes.mapIndexed { index, mode ->
            LabelItem(
                label = ctx.getString(mode.labelId),
                leadingIcon = { mode.Icon() },
                tooltip = ctx.getString(mode.helpTooltipId),
            ) { menuExpanded ->

                G.permissionChain.ask(
                    ctx,
                    mode.requiredPermissions
                ) { granted ->
                    if (granted) {
                        regexModeState.intValue = mode.modeType
                        menuExpanded.value = false
                    }
                }
            }
        }
        items
    }

    DropdownWrapper(
        items = dropdownItems,
    ) { expanded ->

        Box {
            regexModeByType(regexModeState.intValue).Icon()

            // Footer arrow
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_dropdown_footer),
                contentDescription = "",
                tint = C.textGrey,
                modifier = Modifier
                    .size(6.dp)
                    .align(Alignment.BottomEnd)
                    .offset((4).dp, (4).dp)
                    .clickable { expanded.value = true }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditRegexDialog(
    trigger: MutableState<Boolean>,
    forType: Int,
    onSave: Lambda1<RegexRule>,
    showDeleteButton: Boolean = false,
    initRule: RegexRule = RegexRule(), // use default when "Add" new rule
) {
    if (!trigger.value) {
        return
    }

    val ctx = LocalContext.current
    val C = G.palette

    val state = retain { initRule.toState() }

    // if any error, disable the Save button
    val anyError by retain {
        derivedStateOf {
            state.patternError.value || state.patternExtraError.value || state.priorityError.value
        }
    }

    PopupDialog(
        trigger = trigger,
        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 340, maxWidthDp = 600),
        buttons = {
            RowVCenterSpaced(12) {
                // Delete
                if (showDeleteButton) {
                    StrokeButton(
                        label = Str(R.string.delete),
                        color = C.error
                    ) {
                        trigger.value = false

                        // This is only for deleting number
                        G.NumberRuleVM.table.deleteById(ctx, initRule.id)

                        // fire event to update the UI
                        Events.regexRuleUpdated.fire()
                    }
                }

                // Save
                StrokeButton(
                    label = Str(R.string.save),
                    color = if (anyError) C.disabled else C.teal200,
                    enabled = !anyError,
                    onClick = {
                        trigger.value = false

                        val newRule = state.toRule()
                        onSave(newRule)

                        // fire event to update the UI
                        Events.regexRuleUpdated.fire()
                    }
                )
            }
        },
        content = {

            val mode by retain(state.patternModeType.intValue) {
                derivedStateOf {
                    when(forType) {
                        ForNumber -> regexModeByType(state.patternModeType.intValue)
                        ForSms -> RegexMode.SmsContent()
                        else -> RegexMode.QuickCopy()
                    }
                }
            }

            Column {
                mode.Compose(state)
            }
        }
    )
}