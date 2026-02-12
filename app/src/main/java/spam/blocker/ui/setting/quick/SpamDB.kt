package spam.blocker.ui.setting.quick

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.PruneDatabase
import spam.blocker.service.bot.serialize
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyIcon18
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.GreyText
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PriorityBox
import spam.blocker.ui.widgets.PriorityLabel
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.TimeUtils.formatTime
import spam.blocker.util.spf
import java.text.NumberFormat


private const val MAX_LIST_SIZE = 100

private const val SPAM_DB_CLEANUP_WORK_TAG = "spam_db_cleanup_work_tag"

fun reScheduleSpamDBCleanup(ctx: Context) {
    MyWorkManager.cancelByTag(ctx, SPAM_DB_CLEANUP_WORK_TAG)

    val spf = spf.SpamDB(ctx)

    val isEnabled = spf.isEnabled
    val expiryEnabled = spf.isExpiryEnabled
    val ttl = spf.ttl

    if (isEnabled && expiryEnabled && ttl >= 0) {
        MyWorkManager.schedule(
            ctx,
            scheduleConfig = Daily().serialize(),
            actionsConfig = listOf(PruneDatabase(ttl)).serialize(),
            workTag = SPAM_DB_CLEANUP_WORK_TAG
        )
    }
}

@Composable
fun SpamNumCard(
    num: SpamNumber,
    modifier: Modifier = Modifier,
) {
    val C = LocalPalette.current
    val ctx = LocalContext.current

    OutlineCard(
        containerBg = C.dialogBg
    ) {
        RowVCenter(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            // desc
            GreyLabel(text = num.peer, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = M.weight(1f))

            // time
            Text(
                text = formatTime(ctx, num.time),
                fontSize = 14.sp,
                modifier = M
                    .padding(end = 8.dp),
                color = C.textGrey,
                textAlign = TextAlign.Center,
            )
        }
    }
}


@Composable
fun SpamDB() {
    val ctx = LocalContext.current
    val spf = spf.SpamDB(ctx)

    var isEnabled by remember { mutableStateOf(spf.isEnabled) }
    var expiryEnabled by remember { mutableStateOf(spf.isExpiryEnabled) }
    var ttl by remember { mutableIntStateOf(spf.ttl) }
    var priority by remember { mutableIntStateOf(spf.priority) }

    val popupTrigger = rememberSaveable { mutableStateOf(false) }
    var total by remember { mutableIntStateOf(SpamTable.count(ctx)) }

    // Refresh UI on global events, such as workflow action AddToSpamDB and ClearSpamDB
    Events.spamDbUpdated.Listen {
        total = SpamTable.count(ctx)
    }

    // Clear All
    val deleteConfirm = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = deleteConfirm,
        buttons = {
            StrokeButton(label = Str(R.string.clear), color = Salmon) {
                deleteConfirm.value = false
                SpamTable.clearAll(ctx)
                total = SpamTable.count(ctx)
            }
        }
    ) {
        GreyText(Str(R.string.confirm_delete_all_records))
    }

    // Configuration Dialog
    PopupDialog(
        trigger = popupTrigger,
        onDismiss = {
            spf.isExpiryEnabled = expiryEnabled
            spf.ttl = ttl
            reScheduleSpamDBCleanup(ctx)
        }
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Total records   [1234] [clear]
            LabeledRow(labelId = R.string.total) {
                GreyLabel(text = NumberFormat.getInstance().format(total))
                Spacer(modifier = M.width(16.dp))
                StrokeButton(label = Str(R.string.clear), color = Salmon) {
                    deleteConfirm.value = true
                }
            }

            LabeledRow(
                labelId = R.string.expiry,
                helpTooltip = Str(R.string.help_spam_db_ttl),
            ) {
                val trigger = remember { mutableStateOf(false) }
                PopupDialog(trigger = trigger) {
                    // Expiry: 90 days
                    NumberInputBox(
                        intValue = ttl,
                        label = { Text(Str(R.string.days)) },
                        onValueChange = { newVal, hasError ->
                            if (!hasError) {
                                ttl = newVal!!
                            }
                        }
                    )
                }

                if (expiryEnabled) {
                    // Button
                    GreyButton(
                        label = ctx.resources.getQuantityString(R.plurals.days, ttl, ttl),
                    ) { trigger.value = true }
                }

                // Expiry Enabled
                SwitchBox(checked = expiryEnabled, onCheckedChange = { isOn ->
                    expiryEnabled = isOn
                })
            }

            // Priority
            PriorityBox(priority) { newValue, hasError ->
                if (!hasError) {
                    priority = newValue!!
                    spf.priority = newValue
                }
            }


            // Search list
            var keyword by remember { mutableStateOf("") }
            val listState = remember { mutableStateListOf<SpamNumber>() }

            StrInputBox(
                label = { GreyLabel(text = Str(strId = R.string.search_number)) },
                text = keyword,
                leadingIconId = R.drawable.ic_find,
                onValueChange = {
                    keyword = it
                    listState.clear()
                    listState.addAll(SpamTable.search(ctx, keyword, MAX_LIST_SIZE))
                },
                trailingIcon = {
                    GreyIcon18(R.drawable.ic_refresh, modifier = M.clickable {
                        listState.clear()
                        listState.addAll(SpamTable.search(ctx, keyword, MAX_LIST_SIZE))
                    })
                }
            )

            listState.forEach { num ->
                key(num.id) {
                    LeftDeleteSwipeWrapper(
                        left = SwipeInfo(
                            onSwipe = {
                                val index = listState.indexOfFirst { it.id == num.id }

                                // 1. delete from db
                                SpamTable.deleteById(ctx, num.id)
                                // 2. remove from UI
                                listState.removeAt(index)
                                // 3. update total count
                                Events.spamDbUpdated.fire()
                            }
                        )
                    ) {
                        SpamNumCard(num = num)
                    }
                }
            }
        }
    }

    LabeledRow(
        R.string.database,
        helpTooltip = Str(R.string.help_spam_db),
        content = {
            if (isEnabled) {
                Button(
                    content = {
                        RowVCenterSpaced(4) {
                            Text(NumberFormat.getInstance().format(total), color = Salmon)

                            if (priority != 0) {
                                PriorityLabel(priority)
                            }
                        }
                    },
//                    borderColor = Salmon,
                    onClick = {
                        popupTrigger.value = true
                    }
                )
            }
            SwitchBox(isEnabled) { isTurningOn ->
                spf.isEnabled = isTurningOn
                isEnabled = isTurningOn
            }
        }
    )
}