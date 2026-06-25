package spam.blocker.service.bot

import android.content.Context
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.calvin.reorderable.ReorderableColumn
import spam.blocker.G
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.def.Def.ANDROID_12
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.regex.DisableNestedScrolling
import spam.blocker.ui.widgets.ComboBox
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Contacts
import spam.blocker.util.Lambda1
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper

@Serializable
enum class ReplyRuleType {
    Any,
    MeetingMode,
//    NumberRule,
}

@Serializable
@SerialName("TextReplyRule")
data class TextReplyRule(
    val replyText: String = "",

    val contactsOnly: Boolean = true,

    val type: ReplyRuleType = ReplyRuleType.Any,
) {
    fun matches(ctx: Context, aCtx: ActionContext): Boolean {
        if (contactsOnly) {
            Contacts.findContactByRawNumber(ctx, aCtx.rawNumber!!) ?: return false
        }
        return when(type) {
            ReplyRuleType.MeetingMode -> aCtx.checkResult!!.type == Def.RESULT_BLOCKED_BY_MEETING_MODE
            ReplyRuleType.Any -> true
        }
    }
    @Composable
    fun Summary(maxLines: Int = 1) {
        RowVCenterSpaced(4) {
            if (contactsOnly) {
                GreyIcon18(R.drawable.ic_contact_square)
            }
            ResIcon(
                iconId = when (type) {
                    ReplyRuleType.Any -> R.drawable.ic_asterisk
                    ReplyRuleType.MeetingMode -> R.drawable.ic_video_call
                },
                modifier = M.size(18.dp),
                color = G.palette.error,
            )

            GreyLabel(replyText, maxLines = maxLines)
        }
    }
}

@Composable
fun ReplyRuleEditDialog(
    trigger: MutableState<Boolean>,
    rule: TextReplyRule,
    onUpdate: Lambda1<TextReplyRule>
) {
    if (trigger.value) {
        var replyText by retain { mutableStateOf(rule.replyText) }
        var typeIndex by retain { mutableIntStateOf(ReplyRuleType.entries.indexOf(rule.type)) }
        var contactsOnly by retain { mutableStateOf(rule.contactsOnly) }

        PopupDialog(
            trigger,
            onDismiss = {
                val newRule = TextReplyRule(
                    replyText = replyText,
                    contactsOnly = contactsOnly,
                    type = ReplyRuleType.entries[typeIndex],
                )
                if(newRule != rule) {
                    onUpdate(newRule)
                }
            }
        ) {
            // Reply Text
            StrInputBox(
                text = replyText,
                label = {
                    Text(
                        Str(R.string.text_to_reply),
                        color = Color.Unspecified
                    )
                },
                onValueChange = { replyText = it },
                leadingIconId = R.drawable.ic_note,
                maxLines = 10,
            )

            LabeledRow(R.string.is_contact) {
                SwitchBox(checked = contactsOnly, onCheckedChange = { isOn ->
                    contactsOnly = isOn
                })
            }

            LabeledRow(R.string.blocked_by) {
                val items = listOf(
                    Str(R.string.any),
                    Str(R.string.in_meeting),
                )
                ComboBox(
                    items = items.mapIndexed { index, label ->
                        LabelItem(
                            label = label,
                        ) {
                            typeIndex = index
                        }
                    },
                    selected = typeIndex,
                )
            }
        }
    }
}

@Composable
fun ReplyRuleCard(
    rule: TextReplyRule,
    modifier: Modifier
) {
    OutlineCard(containerBg = G.palette.dialogBg, modifier = modifier) {
        Row(
            modifier = M.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            // Features and replyText (Row 1 and row 2)
            Column(
                M.weight(1f).padding(end = 4.dp),
                verticalArrangement = Arrangement.Center
            ) {
                RowVCenterSpaced(2) {
                    Row(modifier = M.weight(1f)) {
                        rule.Summary(maxLines = 2)
                    }
                    GreyIcon16(iconId = R.drawable.ic_reorder)
                }
            }
        }
    }
}


@Serializable
@SerialName("TextReply")
class TextReply(
    var rules: List<TextReplyRule> = listOf()
) : IAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        val requireContactsPermission = rules.any { it.contactsOnly }
        return if(requireContactsPermission)
            listOf(PermissionWrapper(Permission.contacts))
        else
            listOf()
    }
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        if (aCtx.rawNumber == null) {
            aCtx.logger?.error(ctx.getString(R.string.use_global_testing_instead))
            return false
        }

        val r = rules.firstOrNull {
            it.matches(ctx, aCtx)
        }

        if (r == null) {
            return false
        } else {
            aCtx.logger?.debug(ctx.getString(R.string.replying_text).format(r.replyText))
            aCtx.lastOutput = r.replyText
            return true
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.text_reply)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
        rules.firstOrNull()?.Summary()
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_text_reply)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.String)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_note)
    }

    @Composable
    override fun Options() {
        val C = G.palette

        val rulesState = retain { mutableStateListOf(*rules.toTypedArray()) }
        ReorderableColumn(
            list = rulesState.toList(),
            modifier = M.nestedScroll(DisableNestedScrolling()),
            onSettle = { fromIndex, toIndex ->
                rulesState.apply {
                    add(toIndex, removeAt(fromIndex))
                }
                rules = rulesState
            },
            onMove = {},
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) { index, rule, isDragging ->
            key(rule.hashCode()) {
                // Swipe <----
                LeftDeleteSwipeWrapper(
                    left = SwipeInfo(
                        onSwipe = {
                            rulesState -= rule
                            rules = rulesState
                        }
                    )
                ) {
                    val editTrigger = retain { mutableStateOf(false) }
                    ReplyRuleEditDialog(editTrigger, rule) { updatedRule ->
                        rulesState[index] = updatedRule
                        rules = rulesState
                    }

                    ReplyRuleCard(
                        rule = rule,
                        modifier = M
                            .clickable {
                                editTrigger.value = true
                            }
                            .draggableHandle() // make it reorderable
                    )
                }
            }
        }

        LabeledRow(null) {
            StrokeButton(Str(R.string.new_), C.teal200) {
                rulesState += TextReplyRule()
                rules = rulesState
            }
        }
    }
}

@Serializable
@SerialName("SendSms")
class SendSms: IAction {
    override fun requiredPermissions(ctx: Context) = listOf(
        PermissionWrapper(Permission.sendSMS)
    )
    override fun execute(ctx: Context, aCtx: ActionContext): Boolean {
        val logger = aCtx.logger

        if (!Permission.sendSMS.isGranted) {
            logger?.error(ctx.getString(R.string.missing_permission).format(Permission.sendSMS.desc(ctx)))
            return false
        }
        // Android 10/11 requires READ_PHONE_STATE
        if (Build.VERSION.SDK_INT < ANDROID_12 && !Permission.phoneState.isGranted) {
            logger?.error(ctx.getString(R.string.missing_permission).format(Permission.phoneState.desc(ctx)))
            return false
        }

        val toSend = (aCtx.lastOutput as? String) ?: return false

        val rawNumber = aCtx.rawNumber ?: return false

        val smsManager = if (Build.VERSION.SDK_INT >= ANDROID_12) { // Android 12+
            ctx.getSystemService(SmsManager::class.java) ?: SmsManager.getDefault()
        } else { // Android 10 & 11
            val defaultSubId = SubscriptionManager.getDefaultSmsSubscriptionId()

            if (defaultSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                @Suppress("DEPRECATION")
                SmsManager.getSmsManagerForSubscriptionId(defaultSubId)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }

        return try {
            smsManager.sendTextMessage(rawNumber, null, toSend, null, null)
            logger?.warn(ctx.getString(R.string.sms_sent_successfully))
            true
        } catch (e: Exception) {
            logger?.error(ctx.getString(R.string.failed_to_send_sms).format("$e"))
            false
        }
    }

    override fun label(ctx: Context): String {
        return ctx.getString(R.string.send_sms)
    }

    @Composable
    override fun Summary(showIcon: Boolean) {
    }

    override fun tooltip(ctx: Context): String {
        return ctx.getString(R.string.help_action_send_sms)
    }

    override fun inputParamType(): List<ParamType> {
        return listOf(ParamType.String)
    }

    override fun outputParamType(): List<ParamType> {
        return listOf(ParamType.None)
    }

    @Composable
    override fun Icon() {
        GreyIcon(R.drawable.ic_sms)
    }

    @Composable
    override fun Options() {
        NoOptionNeeded()
    }
}
