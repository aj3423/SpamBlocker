package spam.blocker.ui.setting.quick

import android.content.Context
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
import spam.blocker.service.bot.CleanupSpamDB
import spam.blocker.service.bot.Daily
import spam.blocker.service.bot.MyWorkManager
import spam.blocker.service.bot.serialize
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.widgets.GreyButton
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LeftDeleteSwipeWrapper
import spam.blocker.ui.widgets.NumberInputBox
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.RowVCenter
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwipeInfo
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Util
import spam.blocker.util.spf
import java.text.NumberFormat


private const val SPAM_DB_CLEANUP_WORK_TAG = "spam_db_cleanup_work_tag"

fun reScheduleSpamDBCleanup(ctx: Context) {
    MyWorkManager.cancelByTag(ctx, SPAM_DB_CLEANUP_WORK_TAG)

    val ttl = spf.SpamDB(ctx).getTTL()
    if (ttl >= 0) {
        MyWorkManager.schedule(
            ctx,
            scheduleConfig = Daily().serialize(),
            actionsConfig = listOf(CleanupSpamDB(ttl)).serialize(),
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
                text = Util.formatTime(ctx, num.time),
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

    var isEnabled by remember { mutableStateOf(spf.isEnabled()) }
    var ttl by remember { mutableIntStateOf(spf.getTTL()) }

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
        GreyLabel(Str(R.string.confirm_delete_all_records))
    }

    // Configuration Dialog
    PopupDialog(
        trigger = popupTrigger,
        onDismiss = {
            spf.setTTL(ttl)
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

            // Expiry: [90]
            LabeledRow(
                labelId = R.string.expiry,
                helpTooltipId = R.string.help_spam_db_ttl
            ) {
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

            // Search list
            var keyword by remember { mutableStateOf("") }
            val listState = remember { mutableStateListOf<SpamNumber>() }

            StrInputBox(
                label = { GreyLabel(text = Str(strId = R.string.search)) },
                text = keyword,
                leadingIconId = R.drawable.ic_find,
                onValueChange = {
                    keyword = it
                    listState.clear()
                    listState.addAll(SpamTable.search(ctx, keyword, 5))
                }
            )

            listState.forEachIndexed { index, num ->
                key(num.id) {
                    LeftDeleteSwipeWrapper(
                        left = SwipeInfo(
                            onSwipe = {
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
        helpTooltipId = R.string.help_spam_db,
        content = {
            if (isEnabled) {
                GreyButton(
                    label = NumberFormat.getInstance().format(total),
                ) {
                    popupTrigger.value = true
                }
            }
            SwitchBox(isEnabled) { isTurningOn ->
                spf.setEnabled(isTurningOn)
                isEnabled = isTurningOn
            }
        }
    )
}