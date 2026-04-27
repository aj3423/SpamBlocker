package spam.blocker.ui.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import spam.blocker.ui.slightDiff
import spam.blocker.ui.widgets.BUTTON_CORNER_RADIUS
import spam.blocker.ui.widgets.BUTTON_H_PADDING
import spam.blocker.ui.widgets.Button
import spam.blocker.ui.widgets.OutlineCard
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.PopupSize
import spam.blocker.ui.widgets.ResIcon
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
                    modifier = M
                        .padding(start = 4.dp)
                        .weight(1f)
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
                            color = if (record.isBlocked()) C.error else C.success,
                            fontSize = 18.sp
                        )
                    }

                    // Row 2: Geolocation | Carrier
                    val label = listOfNotNull(
                        if (showHistoryGeoLocation.value) Util.numberGeoLocation(ctx, record.peer) else null,
                        if (showHistoryCarrier.value) Util.numberCarrier(ctx, record.peer) else null,
                    ).joinToString(" | ")

                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            style = TextStyle(
                                fontSize = 12.sp,
                                color = C.textGrey.slightDiff(),
                                fontWeight = FontWeight.W500,
                            ),
                            modifier = M.padding(start = 4.dp),
                        )
                    }

                    val r = parseCheckResultFromDb(ctx, record.result, record.reason)

                    // Row 3: Reason Summary
                    RowVCenterSpaced(2) {
                        // Show a yellow "!" if anything went wrong, e.g. ApiQuery timed out
                        if (record.anythingWrong) {
                            ResIcon(
                                R.drawable.ic_exclamation,
                                color = C.warning,
                                modifier = M.size(16.dp)
                            )
                        }

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
                                Text(annotatedLog)
                            }

                            Button(
                                modifier = M.padding(top = 4.dp),
                                contentPadding = PaddingValues(BUTTON_H_PADDING.dp, 2.dp),
                                borderWidth = 0.5.dp,
                                borderColor = C.textGrey,
                                shape = RoundedCornerShape(BUTTON_CORNER_RADIUS.dp),
                                onClick = {
                                    trigger.value = true
                                },
                                content = {
                                    r.ResultReason(true)
                                }
                            )
                        } else {
                            r.ResultReason(false)
                        }
                    }


                    // Report Number / SMS Content
                    r.ExpandedContent(forType, record)
                }

                // 3. SIM / Time
                RowVCenterSpaced(
                    space = 2,
                    modifier = M
                        .padding(end = 8.dp)
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