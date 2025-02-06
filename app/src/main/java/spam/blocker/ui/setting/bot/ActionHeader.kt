package spam.blocker.ui.setting.bot

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.IAction
import spam.blocker.service.bot.InterceptCall
import spam.blocker.service.bot.botActions
import spam.blocker.service.bot.clone
import spam.blocker.service.bot.executeAll
import spam.blocker.ui.M
import spam.blocker.ui.setting.SettingRow
import spam.blocker.ui.setting.api.tagOther
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.BalloonQuestionMark
import spam.blocker.ui.widgets.DimGreyLabel
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Lambda
import spam.blocker.util.TextLogger
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue


@Composable
fun TestActionButton(
    actions: SnapshotStateList<IAction>,
    testingRequireNumber: Boolean = false, // testing InstantQuery requires a number
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current


    var logStr = remember { mutableStateOf(buildAnnotatedString {}) }

    // Log output dialog
    val logTrigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = logTrigger,
    ) {
        Text(text = logStr.value)
    }

    val coroutine = rememberCoroutineScope()
    fun testActions(
        content: String? = null,
    ) {
        logTrigger.value = true
        logStr.value = buildAnnotatedString {} // clear previous log

        coroutine.launch {
            withContext(IO) {
                val aCtx = ActionContext(
                    logger = TextLogger(logStr, C),
                    rawNumber = content,
                    smsContent = content,
                    tagCategory = tagOther,
                )
                actions.executeAll(ctx, aCtx)
            }
        }
    }

    var forCall by remember {
        mutableStateOf(
            // check if the first action is InterceptCall
            actions.firstOrNull() is InterceptCall
        )
    }

    // Input number/sms dialog for InstantQuery
    val inputTrigger = rememberSaveable { mutableStateOf(false) }
    PopupDialog(
        trigger = inputTrigger,
        buttons = {
            StrokeButton(label = Str(R.string.ok), color = Teal200) {
                testActions(if (forCall) G.testingVM.phone.value else G.testingVM.sms.value)
            }
        }
    ) {

        StrInputBox(
            text = if(forCall) G.testingVM.phone.value else G.testingVM.sms.value,
            label = { GreyLabel(Str(if (forCall) R.string.phone_number else R.string.sms_content))},
            placeholder = { DimGreyLabel(if (forCall) "+12223334444" else "") },
            onValueChange = {
                if (forCall)
                    G.testingVM.phone.value = it
                else
                    G.testingVM.sms.value = it
            }
        )
    }

    StrokeButton(label = Str(R.string.test), color = Teal200) {
        if (testingRequireNumber) {
            inputTrigger.value = true
        } else {
            testActions()
        }
    }
}

@Composable
fun ActionPresetCard(
    action: IAction,
    onClick: Lambda,
) {
    val ctx = LocalContext.current

    RowVCenterSpaced(10) {
        // Icon
        action.Icon()
        // Title
        GreyLabel(
            text = action.label(ctx),
            modifier = M
                .weight(1f)
                .clickable { onClick() },
            fontWeight = FontWeight.Bold,
        )
        // Tooltip
        BalloonQuestionMark(tooltip = action.tooltip(ctx))
    }
}

@Composable
fun ActionHeader(
    currentActions: SnapshotStateList<IAction>,
    availableActions: List<IAction> = botActions,
    testingRequireNumber: Boolean = false,
) {
    val trigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = trigger,
    ) {
        availableActions.map { action ->
            ActionPresetCard(
                action = action,
                onClick = {
                    val newAction = action.clone()
                    currentActions.add(newAction)
                    trigger.value = false
                }
            )
        }
    }

    SettingRow(
        modifier = M.fillMaxWidth()
    ) {
        Spacer(modifier = M.weight(1f)) // align the button at the end

        RowVCenterSpaced(4) {
            // Tooltip
            BalloonQuestionMark(Str(R.string.help_action_header))

            // Test
            TestActionButton(
                actions = currentActions,
                testingRequireNumber = testingRequireNumber,
            )

            // New
            StrokeButton(
                label = Str(R.string.new_),
                color = SkyBlue,
            ) {
                trigger.value = true
            }
        }
    }
}

