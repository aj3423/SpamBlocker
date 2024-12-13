package spam.blocker.config

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import spam.blocker.G
import spam.blocker.db.Api
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.service.bot.botJson
import spam.blocker.util.spf
import spam.blocker.util.spf.MeetingAppInfo
import spam.blocker.util.spf.RecentAppInfo


/*
  These default values only works when upgrading from an old version that does not
    have this attribute yet.
 */
@Serializable
class Global {
    var enabled = false
    var callEnabled = false
    var smsEnabled = false
    fun load(ctx: Context) {
        val g = spf.Global(ctx)
        enabled = g.isGloballyEnabled()
        callEnabled = g.isCallEnabled()
        smsEnabled = g.isSmsEnabled()
    }

    fun apply(ctx: Context) {
        spf.Global(ctx).apply {
            setGloballyEnabled(enabled)
            setCallEnabled(callEnabled)
            setSmsEnabled(smsEnabled)
        }
    }
}

@Serializable
class HistoryOptions {
    var showPassed = true
    var showBlocked = true

    var ttl = -1
    var logSmsContent = false
    var initialSmsRowCount = 1

    fun load(ctx: Context) {
        val spf = spf.HistoryOptions(ctx)
        showPassed = spf.getShowPassed()
        showBlocked = spf.getShowBlocked()

        ttl = spf.getTTL()
        logSmsContent = spf.isLogSmsContentEnabled()
        initialSmsRowCount = spf.getInitialSmsRowCount()
    }

    fun apply(ctx: Context) {
        spf.HistoryOptions(ctx).apply {
            setShowPassed(showPassed)
            setShowBlocked(showBlocked)

            setTTL(ttl)
            setLogSmsContentEnabled(logSmsContent)
            setInitialSmsRowCount(initialSmsRowCount)
        }
    }
}

@Serializable
class RegexOptions {
    var numberCollapsed = false
    var contentCollapsed = false
    var quickCopyCollapsed = false
    var maxNoneScrollRows = 10
    var maxRegexRows = 3
    var maxDescRows = 2
    var listHeightPercentage = 60

    fun load(ctx: Context) {
        val spf = spf.RegexOptions(ctx)
        numberCollapsed = spf.isNumberCollapsed()
        contentCollapsed = spf.isContentCollapsed()
        quickCopyCollapsed = spf.isQuickCopyCollapsed()
        maxNoneScrollRows = spf.getMaxNoneScrollRows()
        maxRegexRows = spf.getMaxRegexRows()
        maxDescRows = spf.getMaxDescRows()
        listHeightPercentage = spf.getRuleListHeightPercentage()
    }

    fun apply(ctx: Context) {
        spf.RegexOptions(ctx).apply {
            setNumberCollapsed(numberCollapsed)
            setContentCollapsed(contentCollapsed)
            setQuickCopyCollapsed(quickCopyCollapsed)
            setMaxNoneScrollRows(maxNoneScrollRows)
            setMaxRegexRows(maxRegexRows)
            setMaxDescRows(maxDescRows)
            setRuleListHeightPercentage(listHeightPercentage)
        }
    }
}

@Serializable
class BotOptions {
    var listCollapsed = false

    fun load(ctx: Context) {
        val spf = spf.BotOptions(ctx)
        listCollapsed = spf.isListCollapsed()
    }

    fun apply(ctx: Context) {
        spf.BotOptions(ctx).apply {
            setListCollapsed(listCollapsed)
        }
    }
}

@Serializable
class Theme {
    var type = 0
    fun load(ctx: Context) {
        type = spf.Global(ctx).getThemeType()
    }

    fun apply(ctx: Context) {
        spf.Global(ctx).setThemeType(type)
    }
}

@Serializable
class Language {
    var lang = ""
    fun load(ctx: Context) {
        lang = spf.Global(ctx).getLanguage()
    }

    fun apply(ctx: Context) {
        spf.Global(ctx).setLanguage(lang)
    }
}

@Serializable
class Contact {
    var enabled = false
    var isExcusive = false
    fun load(ctx: Context) {
        val spf = spf.Contact(ctx)
        enabled = spf.isEnabled()
        isExcusive = spf.isExclusive()
    }

    fun apply(ctx: Context) {
        val spf = spf.Contact(ctx)
        spf.setEnabled(enabled)
        spf.setExclusive(isExcusive)
    }
}

@Serializable
class STIR {
    var enabled = false
    var isExcusive = false
    var includeUnverified = false
    fun load(ctx: Context) {
        val spf = spf.Stir(ctx)
        enabled = spf.isEnabled()
        isExcusive = spf.isExclusive()
        includeUnverified = spf.isIncludeUnverified()
    }

    fun apply(ctx: Context) {
        val spf = spf.Stir(ctx)
        spf.setEnabled(enabled)
        spf.setExclusive(isExcusive)
        spf.setIncludeUnverified(includeUnverified)
    }
}
@Serializable
class SpamDB {
    var ttl = 90

    fun load(ctx: Context) {
        val spf = spf.SpamDB(ctx)
        ttl = spf.getTTL()
    }

    fun apply(ctx: Context) {
        spf.SpamDB(ctx).apply {
            setTTL(ttl)
        }
    }
}

@Serializable
class RepeatedCall {
    var enabled = false
    var times = 0
    var inXMin = 0
    fun load(ctx: Context) {
        val spf = spf.RepeatedCall(ctx)
        enabled = spf.isEnabled()
        times = spf.getTimes()
        inXMin = spf.getInXMin()
    }

    fun apply(ctx: Context) {
        val spf = spf.RepeatedCall(ctx)
        spf.setEnabled(enabled)
        spf.setTimes(times)
        spf.setInXMin(inXMin)
    }
}

@Serializable
class Dialed {
    var enabled = false
    var inXDay = 0
    fun load(ctx: Context) {
        val spf = spf.Dialed(ctx)
        enabled = spf.isEnabled()
        inXDay = spf.getDays()
    }

    fun apply(ctx: Context) {
        val spf = spf.Dialed(ctx)
        spf.setEnabled(enabled)
        spf.setDays(inXDay)
    }
}

@Serializable
class BlockType {
    var type = 0
    fun load(ctx: Context) {
        type = spf.BlockType(ctx).getType()
    }

    fun apply(ctx: Context) {
        spf.BlockType(ctx).setType(type)
    }
}

@Serializable
class OffTime {
    var enabled = false
    var stHour = 0
    var stMin = 0
    var etHour = 0
    var etMin = 0
    fun load(ctx: Context) {
        val spf = spf.OffTime(ctx)

        enabled = spf.isEnabled()

        stHour = spf.getStartHour()
        stMin = spf.getStartMin()
        etHour = spf.getEndHour()
        etMin = spf.getEndMin()
    }

    fun apply(ctx: Context) {
        spf.OffTime(ctx).apply {
            setEnabled(enabled)

            setStartHour(stHour)
            setStartMin(stMin)
            setEndHour(etHour)
            setEndMin(etMin)
        }
    }
}

@Serializable
class RecentApps {
    val list = mutableListOf<RecentAppInfo>() // [pkg.a, pkg.b@20, pkg.c]
    var inXMin = 0
    fun load(ctx: Context) {
        val spf = spf.RecentApps(ctx)
        list.clear()
        list.addAll(spf.getList())
        inXMin = spf.getDefaultMin()
    }

    fun apply(ctx: Context) {
        val spf = spf.RecentApps(ctx)
        spf.setList(list)
        spf.setDefaultMin(inXMin)
    }
}

@Serializable
class MeetingMode {
    val list = mutableListOf<MeetingAppInfo>() // [pkg.a, pkg.b@20, pkg.c]
    var priority = 20
    fun load(ctx: Context) {
        val spf = spf.MeetingMode(ctx)
        list.clear()
        list.addAll(spf.getList())
        priority = spf.getPriority()
    }

    fun apply(ctx: Context) {
        val spf = spf.MeetingMode(ctx)
        spf.setList(list)
        spf.setPriority(priority)
    }
}

@Serializable
abstract class PatternRules {
    val rules = mutableListOf<RegexRule>()

    abstract fun table(): RuleTable
    fun load(ctx: Context) {
        rules.clear()
        rules.addAll(table().listAll(ctx))
    }

    fun apply(ctx: Context) {
        val tbl = table()
        tbl.clearAll(ctx)
        rules.forEach {
            tbl.addRuleWithId(ctx, it)
        }
    }
}

@Serializable
class NumberRules : PatternRules() {
    override fun table(): RuleTable {
        return NumberRuleTable()
    }
}

@Serializable
class ContentRules : PatternRules() {
    override fun table(): RuleTable {
        return ContentRuleTable()
    }
}

@Serializable
class QuickCopyRules : PatternRules() {
    override fun table(): RuleTable {
        return QuickCopyRuleTable()
    }
}

@Serializable
class ApiQuery {
    val apis = mutableListOf<Api>()

    fun load(ctx: Context) {
        apis.clear()
        apis.addAll(G.apiQueryVM.table.listAll(ctx))
    }

    fun apply(ctx: Context) {
        val table = G.apiQueryVM.table
        table.clearAll(ctx)
        apis.forEach {
            table.addRecordWithId(ctx, it)
        }
    }
}
@Serializable
class ApiReport {
    val apis = mutableListOf<Api>()

    fun load(ctx: Context) {
        apis.clear()
        apis.addAll(G.apiReportVM.table.listAll(ctx))
    }

    fun apply(ctx: Context) {
        val table = G.apiReportVM.table
        table.clearAll(ctx)
        apis.forEach {
            table.addRecordWithId(ctx, it)
        }
    }
}

@Serializable
class Bots {
    val bots = mutableListOf<Bot>()

    fun load(ctx: Context) {
        bots.clear()
        bots.addAll(BotTable.listAll(ctx))
    }

    fun apply(ctx: Context) {
        BotTable.clearAll(ctx)
        bots.forEach {
            BotTable.addRecordWithId(ctx, it)
        }
    }
}
@Serializable
class SpamNumbers {
    val numbers = mutableListOf<SpamNumber>()

    fun load(ctx: Context) {
        numbers.clear()
        numbers.addAll(SpamTable.listAll(ctx))
    }

    fun apply(ctx: Context) {
        SpamTable.clearAll(ctx)
        SpamTable.addAll(ctx, numbers)
    }
}

@Serializable
class Configs {
    val global = Global()
    val historyOptions = HistoryOptions()
    val regexOptions = RegexOptions()
    val botOptions = BotOptions()
    val theme = Theme()
    val language = Language()

    val contacts = Contact()
    val stir = STIR()
    val spamDB = SpamDB()
    val repeatedCall = RepeatedCall()
    val dialed = Dialed()
    val recentApps = RecentApps()
    val meetingMode = MeetingMode()
    val blockType = BlockType()
    val offTime = OffTime()

    val numberRules = NumberRules()
    val contentRules = ContentRules()
    val quickCopyRules = QuickCopyRules()

    val apiQuery = ApiQuery()
    val apiReport = ApiReport()
    val bots = Bots()

    val spamNumbers = SpamNumbers()

    fun load(ctx: Context, includeSpamDB: Boolean = true) {
        global.load(ctx)
        historyOptions.load(ctx)
        regexOptions.load(ctx)
        botOptions.load(ctx)
        theme.load(ctx)
        language.load(ctx)

        contacts.load(ctx)
        stir.load(ctx)
        spamDB.load(ctx)
        repeatedCall.load(ctx)
        dialed.load(ctx)
        recentApps.load(ctx)
        meetingMode.load(ctx)
        blockType.load(ctx)
        offTime.load(ctx)

        numberRules.load(ctx)
        contentRules.load(ctx)
        quickCopyRules.load(ctx)

        apiQuery.load(ctx)
        apiReport.load(ctx)
        bots.load(ctx)

        if (includeSpamDB)
            spamNumbers.load(ctx)
    }

    fun apply(ctx: Context, includeSpamDB: Boolean = true) {
        global.apply(ctx)
        historyOptions.apply(ctx)
        regexOptions.apply(ctx)
        botOptions.apply(ctx)
        theme.apply(ctx)
        language.apply(ctx)

        contacts.apply(ctx)
        stir.apply(ctx)
        spamDB.apply(ctx)
        repeatedCall.apply(ctx)
        dialed.apply(ctx)
        recentApps.apply(ctx)
        meetingMode.apply(ctx)
        blockType.apply(ctx)
        offTime.apply(ctx)

        numberRules.apply(ctx)
        contentRules.apply(ctx)
        quickCopyRules.apply(ctx)

        apiQuery.apply(ctx)
        apiReport.apply(ctx)
        bots.apply(ctx)

        if (includeSpamDB)
            spamNumbers.apply(ctx)
    }

    fun toJsonString(): String {
        return botJson.encodeToString(this)
    }

    companion object {
        fun createFromJson(jsonStr: String) : Configs {
            val newCfg = botJson.decodeFromString<Configs>(jsonStr)
            return newCfg
        }
    }
}
