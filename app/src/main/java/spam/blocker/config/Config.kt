package spam.blocker.config

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.Contact
import spam.blocker.util.SharedPref.Dialed
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.OffTime
import spam.blocker.util.SharedPref.RecentAppInfo
import spam.blocker.util.SharedPref.RecentApps
import spam.blocker.util.SharedPref.RepeatedCall
import spam.blocker.util.SharedPref.Stir

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
        val g = Global(ctx)
        enabled = g.isGloballyEnabled()
        callEnabled = g.isCallEnabled()
        smsEnabled = g.isSmsEnabled()
    }

    fun apply(ctx: Context) {
        Global(ctx).apply {
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

    fun load(ctx: Context) {
        val spf = spam.blocker.util.SharedPref.HistoryOptions(ctx)
        showPassed = spf.getShowPassed()
        showBlocked = spf.getShowBlocked()

        ttl = spf.getHistoryTTL()
        logSmsContent = spf.isLogSmsContentEnabled()
    }

    fun apply(ctx: Context) {
        spam.blocker.util.SharedPref.HistoryOptions(ctx).apply {
            setShowPassed(showPassed)
            setShowBlocked(showBlocked)

            setHistoryTTL(ttl)
            setLogSmsContentEnabled(logSmsContent)
        }
    }
}

@Serializable
class Theme {
    var type = 0
    fun load(ctx: Context) {
        type = Global(ctx).getThemeType()
    }

    fun apply(ctx: Context) {
        Global(ctx).setThemeType(type)
    }
}

@Serializable
class Language {
    var lang = ""
    fun load(ctx: Context) {
        lang = Global(ctx).getLanguage()
    }

    fun apply(ctx: Context) {
        Global(ctx).setLanguage(lang)
    }
}

@Serializable
class Contact {
    var enabled = false
    var isExcusive = false
    fun load(ctx: Context) {
        val spf = Contact(ctx)
        enabled = spf.isEnabled()
        isExcusive = spf.isExclusive()
    }

    fun apply(ctx: Context) {
        val spf = Contact(ctx)
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
        val spf = Stir(ctx)
        enabled = spf.isEnabled()
        isExcusive = spf.isExclusive()
        includeUnverified = spf.isIncludeUnverified()
    }

    fun apply(ctx: Context) {
        val spf = Stir(ctx)
        spf.setEnabled(enabled)
        spf.setExclusive(isExcusive)
        spf.setIncludeUnverified(includeUnverified)
    }
}

@Serializable
class RepeatedCall {
    var enabled = false
    var times = 0
    var inXMin = 0
    fun load(ctx: Context) {
        val spf = RepeatedCall(ctx)
        enabled = spf.isEnabled()
        times = spf.getTimes()
        inXMin = spf.getInXMin()
    }

    fun apply(ctx: Context) {
        val spf = RepeatedCall(ctx)
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
        val spf = Dialed(ctx)
        enabled = spf.isEnabled()
        inXDay = spf.getDays()
    }

    fun apply(ctx: Context) {
        val spf = Dialed(ctx)
        spf.setEnabled(enabled)
        spf.setDays(inXDay)
    }
}

@Serializable
class BlockType {
    var type = 0
    fun load(ctx: Context) {
        type = BlockType(ctx).getType()
    }

    fun apply(ctx: Context) {
        BlockType(ctx).setType(type)
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
        val spf = OffTime(ctx)

        enabled = spf.isEnabled()

        stHour = spf.getStartHour()
        stMin = spf.getStartMin()
        etHour = spf.getEndHour()
        etMin = spf.getEndMin()
    }

    fun apply(ctx: Context) {
        OffTime(ctx).apply {
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
    val list = mutableListOf<String>() // [pkg.a, pkg.b@20, pkg.c]
    var inXMin = 0
    fun load(ctx: Context) {
        val spf = RecentApps(ctx)
        list.clear()
        list.addAll(spf.getList().map { it.toString() })
        inXMin = spf.getDefaultMin()
    }

    fun apply(ctx: Context) {
        val spf = RecentApps(ctx)
        spf.setList(list.map { RecentAppInfo.fromString(it) })
        spf.setDefaultMin(inXMin)
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
class Configs {
    val global = Global()
    val historyOptions = HistoryOptions()
    val theme = Theme()
    val language = Language()

    val contacts = Contact()
    val stir = STIR()
    val repeatedCall = RepeatedCall()
    val dialed = Dialed()
    val recentApps = RecentApps()
    val blockType = BlockType()
    val offTime = OffTime()

    val numberRules = NumberRules()
    val contentRules = ContentRules()
    val quickCopyRules = QuickCopyRules()

    fun load(ctx: Context) {
        global.load(ctx)
        historyOptions.load(ctx)
        theme.load(ctx)
        language.load(ctx)

        contacts.load(ctx)
        stir.load(ctx)
        repeatedCall.load(ctx)
        dialed.load(ctx)
        recentApps.load(ctx)
        blockType.load(ctx)
        offTime.load(ctx)

        numberRules.load(ctx)
        contentRules.load(ctx)
        quickCopyRules.load(ctx)
    }

    fun apply(ctx: Context) {
        global.apply(ctx)
        historyOptions.apply(ctx)
        theme.apply(ctx)
        language.apply(ctx)

        contacts.apply(ctx)
        stir.apply(ctx)
        repeatedCall.apply(ctx)
        dialed.apply(ctx)
        recentApps.apply(ctx)
        blockType.apply(ctx)
        offTime.apply(ctx)

        numberRules.apply(ctx)
        contentRules.apply(ctx)
        quickCopyRules.apply(ctx)
    }

    fun toJsonString(): String {
        return Json.encodeToString(this)
    }
    fun toPrettyJsonString(): String {
        val prettyJson = Json {
            prettyPrint = true
        }
        val jsonStr = prettyJson.encodeToString(this)
        return jsonStr
    }
    companion object {
        fun createFromJson(jsonStr: String) : Configs {
            val json = Json { ignoreUnknownKeys = true }
            val newCfg = json.decodeFromString<Configs>(jsonStr)
            return newCfg
        }
    }
}

