package spam.blocker.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.db.listReportableAPIs
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.executeAll
import spam.blocker.service.checker.parseCheckResultFromDb
import spam.blocker.ui.M
import spam.blocker.ui.history.HistoryOptions.forceShowSIM
import spam.blocker.ui.history.HistoryOptions.showHistoryGeoLocation
import spam.blocker.ui.setting.api.spamCategoryNamesMap
import spam.blocker.ui.setting.api.tagValid
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SimCardIcon
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Contacts
import spam.blocker.util.JetpackTextLogger
import spam.blocker.util.TimeUtils.FreshnessColor
import spam.blocker.util.TimeUtils.formatTime
import spam.blocker.util.TimeUtils.timeColor
import spam.blocker.util.Util
import spam.blocker.util.logi
import androidx.compose.foundation.Image as ComposeImage


// The default values when not expanded
const val CardHeight = 64 // the height when RegexStr is single line
const val CardPaddingVertical = 8 // the top/bottom padding
const val ItemHeight = CardHeight - 2 * CardPaddingVertical // the height of Avatar and Time


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportSpamDialog(
    trigger: MutableState<Boolean>,
    rawNumber: String,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current
    val scope = CoroutineScope(IO)

    PopupDialog(
        trigger = trigger,
    ) {
        val nameMap = spamCategoryNamesMap(ctx)

        val keyTags = nameMap.keys.toList()

        val reportResult = remember { mutableStateOf(buildAnnotatedString {  }) }

        // Category buttons
        FlowRowSpaced (
            space = 20,
            vSpace = 30,
        ) {
            keyTags.forEach { keyTag ->
                StrokeButton(
                    label = nameMap[keyTag]!!,
                    color = if(keyTag == tagValid) Color.Cyan else  Color(keyTag.hashCode().toLong() or 0xffc08080),
                ) {
                    reportResult.value = buildAnnotatedString {  } // clear prev result

                    val apis = listReportableAPIs(ctx = ctx, rawNumber = rawNumber, domainFilter = null, isManualReport = true, blockReason = null)
                    apis.forEach { api ->
                        scope.launch {
                            withContext(IO) {
                                val aCtx = ActionContext(
                                    scope = scope,
                                    logger = JetpackTextLogger(reportResult, C),
                                    rawNumber = rawNumber,
                                    tagCategory = keyTag,
                                )

                                val success = api.actions.executeAll(ctx, aCtx)
                                logi("report number $rawNumber to ${api.summary()}, success: $success")
                            }
                        }
                    }
                }
            }
        }
        if (reportResult.value.text.isNotEmpty()) {
            Spacer(modifier = M.height(10.dp))
            Text(text = reportResult.value, color = C.textGrey)
        }
    }
}

@Composable
fun HistoryCard(
    forType: Int,
    record: HistoryRecord,
    indicators: Indicators,
    simCount: Int,
    timeColors: List<FreshnessColor>?,
    modifier: Modifier,
) {
    val C = LocalPalette.current
    val ctx = LocalContext.current
    OutlineCard(
        modifier = M.animateContentSize(),
        borderColor = if (record.isTest) Teal200 else C.cardBorder
    ) {
        Box(
            modifier = M
                .wrapContentSize()
        ) {
            RowVCenterSpaced(
                space = 2,
                modifier = modifier.padding(8.dp)
            ) {

                // 1. avatar
                val contact = Contacts.findContactByRawNumber(ctx, record.peer)
                val bmpAvatar = contact?.loadAvatar(ctx)
                if (bmpAvatar != null) {
                    ComposeImage(
                        bmpAvatar.asImageBitmap(), "", modifier = M
                            .size(ItemHeight.dp)
                            .align(Alignment.Top)
                            .clip(RoundedCornerShape((ItemHeight / 2).dp))
                    )
                } else {
                    // Use the hash code as color
                    val toHash = contact?.name ?: record.peer
                    val color = Color(toHash.hashCode().toLong() or 0xff808080/* for higher contrast */)
                    ResImage(
                        R.drawable.ic_contact_circle, color = color, modifier = M
                            .size(ItemHeight.dp)
                            .align(Alignment.Top)
                    )
                }

                // 2. Rule indicator / Number / BlockReason / SMS Content
                Column(
                    modifier = M.padding(start = 4.dp).weight(1f)
                ) {
                    // Row 1: Rule indicator / Number
                    RowVCenterSpaced(2) {
                        // Db/Rule existence indicators
                        if(indicators.isNotEmpty())
                            IndicatorIcons(indicators)

                        // Number
                        var t = contact?.name ?: record.peer
                        // Display Name (CNAP)
                        if (!record.cnap.isNullOrEmpty()) {
                            t += " (${record.cnap})"
                        }
                        Text(
                            text = t,
                            color = if (record.isBlocked()) C.block else C.pass,
                            fontSize = 18.sp
                        )
                    }

                    // Row 2: Geo Location
                    if (showHistoryGeoLocation.value) {
                        val loc = Util.numberGeoLocation(ctx, record.peer)
                        loc?.let {
                            Text(
                                text = loc,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = C.textDimGrey,
                                    fontWeight = FontWeight.W500,
                                ),
                                modifier = M.padding(start = 4.dp),
                            )
                        }
                    }

                    // Row 3: Reason Summary
                    val r = parseCheckResultFromDb(ctx, record.result, record.reason)
                    r.ResultReason(record.expanded)

                    // Report Number / SMS Content
                    r.ExpandedContent(forType, record)
                }

                // 3. SIM / Time
                RowVCenterSpaced(
                    space = 2,
                    modifier = M.padding(end = 8.dp)
                        .align(Alignment.Top)
                        .heightIn(ItemHeight.dp)
                ) {
                    // SIM slot icon
                    if ((simCount >= 2 || forceShowSIM.value) && record.simSlot != null) {
                        SimCardIcon(
                            record.simSlot,
                        )
                    }

                    // time
                    val color = if (timeColors.isNullOrEmpty()) {
                        C.textGrey
                    } else {
                        timeColor(record.time, timeColors) ?: C.textGrey
                    }
                    Text(
                        text = formatTime(ctx, record.time),
                        fontSize = 14.sp,
                        color = color,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Unread red dot
            if (!record.read) {
                Canvas(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = (6).dp)
                ) {
                    drawCircle(color = Salmon, radius = size.minDimension / 2)
                }
            }

            // Test Tube
            if (record.isTest) {
                ResIcon(
                    R.drawable.ic_tube,
                    color = Teal200,
                    modifier = M.size(16.dp).align(Alignment.BottomEnd).offset(x = (-4).dp, y = (-4).dp)
                )
            }
        }
    }
}