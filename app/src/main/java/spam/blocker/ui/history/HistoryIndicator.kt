package spam.blocker.ui.history

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CNAP_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTENT_RULE
import spam.blocker.def.Def.RESULT_ALLOWED_BY_GEO_LOCATION_REGEX
import spam.blocker.def.Def.RESULT_ALLOWED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CNAP_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTACT_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT_RULE
import spam.blocker.def.Def.RESULT_BLOCKED_BY_GEO_LOCATION_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER_REGEX
import spam.blocker.def.Def.RESULT_BLOCKED_BY_SPAM_DB
import spam.blocker.def.Def.isBlocked
import spam.blocker.service.checker.ByRegexRule
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.IChecker
import spam.blocker.service.checker.toChecker
import spam.blocker.ui.M
import spam.blocker.ui.history.HistoryOptions.showHistoryIndicator
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.util.spf

data class Indicator(
    val type: Int, // same as CheckResult.type, e.g.: RESULT_BLOCKED_BY_SPAM_DB
    val priority: Int, // 0 for spamDB, or rule.priority
)
typealias Indicators = List<Indicator>

@Composable
fun IndicatorIcons(indicators: Indicators) {
    val C = LocalPalette.current

    RowVCenterSpaced(2) {
        // Db existence indicator
        indicators.sortedByDescending { it.priority }.forEach {
            when (it.type) {
                RESULT_BLOCKED_BY_SPAM_DB -> {
                    ResIcon(R.drawable.ic_db_delete, modifier = M.size(16.dp), color = C.block)
                }

                RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX -> {
                    ResIcon(R.drawable.ic_number_sign, modifier = M.size(16.dp), color = if(isBlocked(it.type)) C.block else C.pass)
                }
                RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX -> {
                    ResIcon(R.drawable.ic_contact_square, modifier = M.size(16.dp), color = if(isBlocked(it.type)) C.block else C.pass)
                }
                RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX -> {
                    ResIcon(R.drawable.ic_contact_group, modifier = M.size(16.dp), color = if(isBlocked(it.type)) C.block else C.pass)
                }
                RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX -> {
                    ResIcon(R.drawable.ic_id_card, modifier = M.size(16.dp), color = if(isBlocked(it.type)) C.block else C.pass)
                }
                RESULT_ALLOWED_BY_GEO_LOCATION_REGEX, RESULT_BLOCKED_BY_GEO_LOCATION_REGEX -> {
                    ResIcon(R.drawable.ic_location, modifier = M.size(16.dp), color = if(isBlocked(it.type)) C.block else C.pass)
                }

                RESULT_ALLOWED_BY_CONTENT_RULE -> {
                    ResIcon(R.drawable.ic_sms_pass, modifier = M.size(14.dp), color = C.pass)
                }

                RESULT_BLOCKED_BY_CONTENT_RULE -> {
                    ResIcon(R.drawable.ic_sms_blocked, modifier = M.size(14.dp), color = C.block)
                }
            }
        }
    }
}

// Keep track of:
//  1. number's existence in the spam database.
//  2. number matches any regex rule
//  3. sms content matches regex rule
// and show red/green indicators(icons) before the number
@Composable
fun IndicatorsWrapper(
    vm: HistoryViewModel,
    content: @Composable (
            (number: String, cnap: String?, smsContent: String?, simSlot: Int?) -> Indicators,
            Boolean,
    ) -> Unit,
) {
    val ctx = LocalContext.current

    // Just a short alias, that G.xxx it too long
    var showIndicator by remember(showHistoryIndicator.value) {
        mutableStateOf(showHistoryIndicator.value)
    }

    var onRefresh by remember { mutableStateOf(false) }

    // load from tables and generate ICheckers
    fun loadNumberCheckers(): List<IChecker> {
        return if (showIndicator) {
            NumberRegexTable().listAll(ctx).map {
                it.toChecker(ctx)
            }
        } else listOf()
    }

    fun loadContentCheckers(): List<IChecker> {
        return if (showIndicator && vm.forType == Def.ForSms) {
            ContentRegexTable().listAll(ctx).map {
                Checker.Content(ctx, it)
            }
        } else listOf()
    }

    var numberCheckers = remember(showIndicator) { loadNumberCheckers() }
    var contentCheckers = remember(showIndicator) { loadContentCheckers() }

    // Refresh the list on regex change or spam db change
    Events.regexRuleUpdated.Listen {
        numberCheckers = loadNumberCheckers()
        contentCheckers = loadContentCheckers()
        onRefresh = !onRefresh // refresh all records
    }
    Events.spamDbUpdated.Listen {
        onRefresh = !onRefresh // refresh all records
    }

    fun check(number: String, cnap: String?, smsContent: String?, simSlot: Int?): Indicators {
        return buildList {
            // 1. Number exist in spam db?
            run {
                if (SpamTable.findByNumber(ctx, number) != null) {
                    add(
                        Indicator(type = RESULT_BLOCKED_BY_SPAM_DB, priority = spf.SpamDB(ctx).priority)
                    )
                }
            }

            if (vm.forType == Def.ForNumber) { // in Call Tab
                // 2. Check if the call number matches any Number Rule?
                run {
                    val checkResult = Checker.checkCall(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        cnap = cnap,
                        checkers = numberCheckers,
                        simSlot = simSlot
                    )
                    val resultType = checkResult.type

                    when (resultType) {
                        RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX,
                        RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX,
                        RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX,
                        RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX,
                        RESULT_ALLOWED_BY_GEO_LOCATION_REGEX, RESULT_BLOCKED_BY_GEO_LOCATION_REGEX
                             -> {
                                add(
                                    Indicator(
                                        type = resultType,
                                        priority = (checkResult as ByRegexRule).rule?.priority
                                            ?: -1, // -1: rule deleted
                                    )
                                )
                             }
                    }
                }
            } else { // in SMS Tab

                // 2. Check if the sms number matches any Number Rule?
                run {
                    val checkResult = Checker.checkSms(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        messageBody = smsContent ?: "",
                        simSlot = simSlot,
                        checkers = numberCheckers,
                    )
                    val resultType = checkResult.type

                    when (resultType) {
                        RESULT_ALLOWED_BY_NUMBER_REGEX, RESULT_BLOCKED_BY_NUMBER_REGEX,
                        RESULT_ALLOWED_BY_CONTACT_REGEX, RESULT_BLOCKED_BY_CONTACT_REGEX,
                        RESULT_ALLOWED_BY_CONTACT_GROUP_REGEX, RESULT_BLOCKED_BY_CONTACT_GROUP_REGEX,
                        RESULT_ALLOWED_BY_CNAP_REGEX, RESULT_BLOCKED_BY_CNAP_REGEX,
                        RESULT_ALLOWED_BY_GEO_LOCATION_REGEX, RESULT_BLOCKED_BY_GEO_LOCATION_REGEX
                            -> {
                                add(
                                    Indicator(
                                        type = resultType,
                                        priority = (checkResult as ByRegexRule).rule?.priority
                                            ?: -1, // -1: rule deleted
                                    )
                                )
                            }
                    }
                }
                // 3. Check if the SMS matches any Content Rule?
                run {
                    val checkResult = Checker.checkSms(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        messageBody = smsContent ?: "",
                        simSlot = simSlot,
                        checkers = contentCheckers,
                    )
                    val resultType = checkResult.type

                    when (checkResult.type) {
                        RESULT_ALLOWED_BY_CONTENT_RULE, RESULT_BLOCKED_BY_CONTENT_RULE -> {
                            add(
                                Indicator(
                                    type = resultType,
                                    priority = (checkResult as ByRegexRule).rule?.priority
                                        ?: -1, // -1: rule deleted
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    content(::check, onRefresh)
}
