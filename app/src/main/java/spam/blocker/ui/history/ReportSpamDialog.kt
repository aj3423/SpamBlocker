package spam.blocker.ui.history

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import spam.blocker.G
import spam.blocker.db.listReportableAPIs
import spam.blocker.service.bot.ActionContext
import spam.blocker.service.bot.executeAll
import spam.blocker.ui.M
import spam.blocker.ui.setting.api.spamCategoryNamesMap
import spam.blocker.ui.setting.api.tagValid
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.JetpackTextLogger
import spam.blocker.util.logi


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportSpamDialog(
    trigger: MutableState<Boolean>,
    rawNumber: String,
) {
    val ctx = LocalContext.current
    val C = G.palette
    val scope = CoroutineScope(IO)

    PopupDialog(
        trigger = trigger,
    ) {
        val nameMap = spamCategoryNamesMap(ctx)

        val keyTags = nameMap.keys.toList()

        val reportResult = remember { mutableStateOf(buildAnnotatedString {  }) }

        var comment by remember { mutableStateOf("")}

//        StrInputBox(
//            text = comment,
//            label = { Text(Str(R.string.comment_optional))},
//            onValueChange = {
//                comment = it
//            }
//        )
//        Spacer(modifier = M.height(8.dp))

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
                                    logger = JetpackTextLogger(reportResult),
                                    rawNumber = rawNumber,
                                    tagCategoryValue = keyTag,
                                    tagCommentValue = comment
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
