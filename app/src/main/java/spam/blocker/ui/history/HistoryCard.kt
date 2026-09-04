package spam.blocker.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.HistoryRecord
import spam.blocker.service.checker.parseCheckResultFromDb
import spam.blocker.ui.M
import spam.blocker.ui.history.HistoryOptions.forceShowSIM
import spam.blocker.ui.history.HistoryOptions.showHistoryCarrier
import spam.blocker.ui.history.HistoryOptions.showHistoryGeoLocation
import spam.blocker.ui.priorityInlineMap
import spam.blocker.ui.slightDiff
import spam.blocker.ui.widgets.BUTTON_CORNER_RADIUS
import spam.blocker.ui.widgets.BUTTON_H_PADDING
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.ResIcon16
import spam.blocker.ui.widgets.ResIcon20
import spam.blocker.ui.widgets.ResImage
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.SimCardIcon
import spam.blocker.util.Contacts
import spam.blocker.util.MarkupText
import spam.blocker.util.PermissiveJson
import spam.blocker.util.TimeUtils.FreshnessColor
import spam.blocker.util.TimeUtils.formatTime
import spam.blocker.util.TimeUtils.timeColor
import spam.blocker.util.Util
import androidx.compose.foundation.Image as ComposeImage


// The default values when not expanded
const val CardHeight = 64 // the height when RegexStr is single line
const val CardPaddingVertical = 8 // the top/bottom padding
const val ItemHeight = CardHeight - 2 * CardPaddingVertical // the height of Avatar and Time


@Composable
fun HistoryCard(
    forType: Int,
    record: HistoryRecord,
    indicators: Indicators,
    simCount: Int,
    timeColors: List<FreshnessColor>?,
    modifier: Modifier,
) {
    val C = G.palette
    val ctx = LocalContext.current
    OutlineCard(
        modifier = M.animateContentSize(),
        borderColor = if (record.isTest) C.teal200 else C.dialogBg.slightDiff()
    ) {
        Box(
            modifier = M
                .wrapContentSize()
        ) {
            Column(
                modifier = modifier.padding(8.dp)
            ) {
                val r = remember(record.result, record.reason) { parseCheckResultFromDb(ctx, record.result, record.reason) }
                val contact = remember(record.peer) { Contacts.findContactByRawNumber(ctx, record.peer) }

                @Composable
                fun Avatar(modifier: Modifier) {
                    val bmpAvatar = contact?.loadAvatar(ctx)
                    if (bmpAvatar != null) {
                        ComposeImage(bmpAvatar.asImageBitmap(), "", modifier = modifier)
                    } else {
                        // Use the hash code as color
                        val toHash = contact?.name ?: record.peer
                        val color = Color(toHash.hashCode().toLong() or 0xff808080/* for higher contrast */)
                        ResImage(R.drawable.ic_contact_circle, color = color, modifier = modifier)
                    }
                }

                @Composable
                fun SimAndTime(modifier: Modifier = Modifier) {
                    RowVCenterSpaced(2, modifier = modifier) {
                        if ((simCount >= 2 || forceShowSIM.value) && record.simSlot != null) {
                            SimCardIcon(
                                record.simSlot,
                            )
                        }
                        Text(
                            text = formatTime(ctx, record.time),
                            fontSize = 14.sp,
                            color = if (timeColors.isNullOrEmpty()) {
                                C.textGrey
                            } else {
                                timeColor(record.time, timeColors) ?: C.textGrey
                            },
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                RowVCenterSpaced(
                    space = 2,
                ) {

                    // 1. avatar (only when not expanded)
                    if (!record.expanded) {
                        Avatar(M
                            .size(ItemHeight.dp)
                            .align(Alignment.Top)
                            .clip(RoundedCornerShape((ItemHeight / 2).dp))
                        )
                    }
                    // 2. Rule indicator / Number / BlockReason
                    Column(
                        modifier = M
                            .padding(start = if (!record.expanded) 4.dp else 0.dp)
                            .weight(1f)
                    ) {
                        // Row 1: Contact Avatar (when expanded) / Rule indicator / Number / SIM / Time
                        RowVCenterSpaced(2) {
                            if (record.expanded) {
                                Avatar(M
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                )
                            }

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
                                color = if (record.isBlocked()) C.error else C.success,
                                fontSize = 18.sp,
                                modifier = M.weight(1f)
                            )

                            if (record.expanded) {
                                SimAndTime()
                            }
                        }

                        // Row 2: Geolocation | Carrier
                        val label = remember(record.peer, showHistoryGeoLocation.value, showHistoryCarrier.value) {
                            listOfNotNull(
                                if (showHistoryGeoLocation.value) Util.numberGeoLocation(ctx, record.peer) else null,
                                if (showHistoryCarrier.value) Util.numberCarrier(ctx, record.peer) else null,
                            ).joinToString(" | ")
                        }

                        if (label.isNotEmpty()) {
                            Text(
                                text = label,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = C.textGrey.slightDiff(),
                                    fontWeight = FontWeight.W500,
                                ),
                                modifier = M.padding(start = if (!record.expanded) 4.dp else 0.dp),
                            )
                        }

                        // Row 3: Block Reason
                        RowVCenterSpaced(4, M.padding(vertical = if(record.expanded) 4.dp else 0.dp)) {
                            // Show a label when not expanded, and a clickable button when expanded
                            if (record.expanded) {
                                val trigger = remember { mutableStateOf(false) }

                                // Show full screening log
                                PopupDialog(
                                    trigger = trigger,
                                    popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 320, maxWidthDp = 1200),
                                ) {
                                    val annotatedLog = remember {
                                        try {
                                            val t = PermissiveJson.decodeFromString<MarkupText>(
                                                record.fullScreeningLog?: ""
                                            )
                                            t.toAnnotatedString()
                                        } catch (_: Exception) {
                                            AnnotatedString("")
                                        }
                                    }
                                    Text(
                                        text = annotatedLog,
                                        inlineContent = priorityInlineMap()
                                    )
                                }

                                // Block Reason
                                Row(M.weight(1f)) {
                                    Button(
                                        contentPadding = PaddingValues(BUTTON_H_PADDING.dp, 2.dp),
                                        borderWidth = 0.5.dp,
                                        borderColor = C.textGrey,
                                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp),
                                        onClick = {
                                            trigger.value = true
                                        },
                                        content = {
                                            RowVCenterSpaced(2) {
                                                // Show a yellow "!" if anything went wrong during screening, e.g. ApiQuery timed out
                                                if (record.anythingWrongScreening) {
                                                    ResIcon16(R.drawable.ic_exclamation, color = C.warning)
                                                }

                                                r.ResultReason(true)
                                            }
                                        }
                                    )
                                }
                            } else { // record not expanded
                                RowVCenterSpaced(2, modifier = M.weight(1f)) {
                                    // Show a yellow "!" if anything went wrong during screening, e.g. ApiQuery timed out
                                    if (record.anythingWrongScreening) {
                                        ResIcon16(R.drawable.ic_exclamation, color = C.warning)
                                    }
                                    r.ResultReason(false)
                                }
                            }

                            // Auto/Manual Report Log
                            if (record.autoReportingLog != null) {
                                val trigger = remember { mutableStateOf(false) }
                                val iconColor = if (record.anythingWrongReporting) C.warning else C.success

                                if (record.expanded) {
                                    // Show full screening log
                                    PopupDialog(
                                        trigger = trigger,
                                        popupSize = PopupSize(maxWidthPercentage = 0.9f, minWidthDp = 320, maxWidthDp = 1200),
                                    ) {
                                        val annotatedLog = remember {
                                            try {
                                                val t = PermissiveJson.decodeFromString<MarkupText>(
                                                    record.autoReportingLog
                                                )
                                                t.toAnnotatedString()
                                            } catch (_: Exception) {
                                                AnnotatedString("")
                                            }
                                        }
                                        Text(
                                            text = annotatedLog,
                                        )
                                    }
                                    Button(
                                        borderWidth = 0.5.dp,
                                        borderColor = iconColor,
                                        shape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp),
                                        onClick = {
                                            trigger.value = true
                                        },
                                        content = {
                                            ResIcon20(
                                                iconId = R.drawable.ic_upload_to_cloud,
                                                color = iconColor,
                                            )
                                        }
                                    )
                                } else {
                                    ResIcon20(
                                        iconId = R.drawable.ic_upload_to_cloud,
                                        color = iconColor,
                                    )
                                }
                            }
                        }
                    }

                    // 3. SIM / Time (only when not expanded)
                    if (!record.expanded) {
                        SimAndTime(M.heightIn(ItemHeight.dp))
                    }
                }

                // Report Number / SMS Content
                r.ExpandedContent(forType, record)
            }

            // Unread red dot
            if (!record.read) {
                Canvas(
                    modifier = Modifier
                        .size(4.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = (-6).dp, y = (6).dp)
                ) {
                    drawCircle(color = C.error, radius = size.minDimension / 2)
                }
            }

            // Test Tube
            if (record.isTest) {
                ResIcon(
                    R.drawable.ic_tube,
                    color = C.teal200,
                    modifier = M
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                )
            }
        }
    }
}