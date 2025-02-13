package spam.blocker.ui.history

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.SpamTable
import spam.blocker.def.Def
import spam.blocker.def.Def.RESULT_ALLOWED_BY_CONTENT
import spam.blocker.def.Def.RESULT_ALLOWED_BY_NUMBER
import spam.blocker.def.Def.RESULT_BLOCKED_BY_CONTENT
import spam.blocker.def.Def.RESULT_BLOCKED_BY_NUMBER
import spam.blocker.service.checker.Checker
import spam.blocker.service.checker.IChecker
import spam.blocker.service.checker.toNumberChecker
import spam.blocker.ui.M
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.RowVCenterSpaced


data class Indicators(
    var inDb: Boolean = false,
    var inNumberRule: Boolean? = null, // null: not exist, true: in whitelist rule, false: in blacklist rule
    var inContentRule: Boolean? = null,
) {
    fun any(): Boolean {
        return inDb || inNumberRule != null || inContentRule != null
    }
}


@Composable
fun IndicatorIcons(indicators: Indicators) {
    val C = LocalPalette.current

    RowVCenterSpaced(2) {
        // Db existence indicator
        val inDb = indicators.inDb
        if (inDb) {
            ResIcon(R.drawable.ic_db_delete, modifier = M.size(16.dp), color = C.block)
        }

        // number indicator
        val numberMatch = indicators.inNumberRule
        if (numberMatch != null) {
            ResIcon(
                R.drawable.ic_number_sign,
                modifier = M.size(16.dp),
                color = if (numberMatch) C.pass else C.block
            )
        }

        // content indicator
        val contentMatch = indicators.inContentRule
        if (contentMatch != null) {
            ResIcon(
                if (contentMatch) R.drawable.ic_sms_pass else R.drawable.ic_sms_blocked,
                modifier = M.size(14.dp),
                color = if (contentMatch) C.pass else C.block
            )
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
            (number: String?, smsContent: String?) -> Indicators,
            Boolean,
    ) -> Unit,
) {

    val ctx = LocalContext.current

    var onRefresh by remember { mutableStateOf(false) }

    // load from tables and generate ICheckers
    fun loadNumberCheckers(): List<IChecker> {
        return if (vm.showIndicator.value) {
            NumberRuleTable().listRules(ctx, Def.FLAG_FOR_CALL).map {
                it.toNumberChecker(ctx)
            }
        } else listOf()
    }

    fun loadContentCheckers(): List<IChecker> {
        return if (vm.showIndicator.value && vm.forType == Def.ForSms) {
            ContentRuleTable().listRules(ctx, Def.FLAG_FOR_SMS).map {
                Checker.Content(ctx, it)
            }
        } else listOf()
    }

    var numberCheckers = remember(vm.showIndicator.value) { loadNumberCheckers() }
    var contentCheckers = remember(vm.showIndicator.value) { loadContentCheckers() }

    // These maps are caches of the existences
    val dbCache = remember { mutableStateMapOf<String, Boolean>() }
    val numberCache = remember { mutableStateMapOf<String, Boolean?>() }
    val contentCache = remember { mutableStateMapOf<String, Boolean?>() }

    // on regex change event, refresh the `numberCheckers` and `contentRegexes`
    Events.regexRuleUpdated.Listen {
        numberCheckers = loadNumberCheckers()
        numberCache.clear()
        contentCheckers = loadContentCheckers()
        contentCache.clear()
        onRefresh = !onRefresh
    }

    fun check(number: String?, smsContent: String?): Indicators {
        return Indicators().apply {
            number?.let {
                // 1. exist in database?
                inDb = dbCache.getOrPut(number) { SpamTable.findByNumber(ctx, number) != null }

                // 2. exist in number rule?
                inNumberRule = numberCache.getOrPut(number) {
                    val checkResult = Checker.checkCallWithCheckers(
                        ctx = ctx,
                        logger = null,
                        rawNumber = number,
                        checkers = numberCheckers,
                    )

                    when (checkResult.type) {
                        RESULT_ALLOWED_BY_NUMBER -> true
                        RESULT_BLOCKED_BY_NUMBER -> false
                        else -> null
                    }
                }
            }

            // 3. exist in SMS content rule?
            smsContent?.let {
                inContentRule = contentCache.getOrPut(smsContent) {

                    val checkResult = Checker.checkSmsWithCheckers(
                        ctx = ctx,
                        logger = null,
                        rawNumber = "",
                        messageBody = smsContent,
                        checkers = contentCheckers,
                    )
                    when (checkResult.type) {
                        RESULT_ALLOWED_BY_CONTENT -> true
                        RESULT_BLOCKED_BY_CONTENT -> false
                        else -> null
                    }
                }

            }
        }
    }

    content(::check, onRefresh)
}
