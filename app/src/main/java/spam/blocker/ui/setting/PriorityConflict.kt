package spam.blocker.ui.setting

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.service.checker.Checker.Companion.defaultCallCheckers
import spam.blocker.service.checker.Checker.Companion.defaultSmsCheckers
import spam.blocker.service.checker.IChecker
import spam.blocker.service.checker.findConflicts
import spam.blocker.ui.M
import spam.blocker.ui.priorityInlineMap
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.A
import spam.blocker.util.spf

fun detectConflictCheckers(ctx: Context) : List<IChecker> {
    val callCheckers = defaultCallCheckers(ctx)
        .filter { it.isConfigEnabledForCall() }
        .findConflicts()

    val smsCheckers = defaultSmsCheckers(ctx)
        .filter { it.isConfigEnabledForSms() }
        .findConflicts()

    return (callCheckers + smsCheckers)
        .distinctBy { it.desc() }

}

@Composable
fun PriorityConflictDialog(
    trigger: MutableState<Boolean>,
    conflicts: List<IChecker>
) {
    val C = G.palette
    val ctx = LocalContext.current

    PopupDialog(
        trigger = trigger,
        icon = { ResIcon(R.drawable.ic_warning, color = Color.Unspecified) },
        buttons = {
            StrokeButton(Str(R.string.ignore), C.warning) {
                spf.Global(ctx).ignorePriorityConflict = true
                trigger.value = false
            }
        }
    ) {
        Column {
            HtmlText(Str(R.string.warning_priority_conflict), modifier = M.padding(bottom = 8.dp))
            conflicts.sortedBy { it.priority() }.forEach {
                Text(
                    text = buildAnnotatedString {
                        appendInlineContent(id = "priority")

                        append(if(it.priority() == Int.MAX_VALUE) {
                            Str(R.string.max).A(C.priority)
                        } else {
                            it.priority().toString().A(C.priority)
                        })

                        append(" ")

                        append(it.desc().text.A(
                            if (it.listType() == true)
                                C.teal200
                            else
                                C.error
                        ))
                    },
                    inlineContent = priorityInlineMap()
                )
            }
        }
    }
}