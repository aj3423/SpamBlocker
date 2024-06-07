package spam.blocker.util

import android.content.Context
import kotlinx.serialization.Serializable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.PatternRule
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RuleTable
import spam.blocker.util.SharedPref.BlockType
import spam.blocker.util.SharedPref.Contact
import spam.blocker.util.SharedPref.Dialed
import spam.blocker.util.SharedPref.Global
import spam.blocker.util.SharedPref.OffTime
import spam.blocker.util.SharedPref.RecentApps
import spam.blocker.util.SharedPref.RepeatedCall
import spam.blocker.util.SharedPref.Stir

class Config {
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
    class Theme {
        var isDark = false
        fun load(ctx: Context) { isDark = Global(ctx).isDarkTheme() }
        fun apply(ctx: Context) { Global(ctx).setDarkTheme(isDark) }
    }
    @Serializable
    class Language {
        var lang = ""
        fun load(ctx: Context) { lang = Global(ctx).getLanguage() }
        fun apply(ctx: Context) { Global(ctx).setLanguage(lang) }
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
            val (tms, inxmin) = spf.getConfig()
            times = tms
            inXMin = inxmin
        }
        fun apply(ctx: Context) {
            val spf = RepeatedCall(ctx)
            spf.setEnabled(enabled)
            spf.setConfig(times, inXMin)
        }
    }
    @Serializable
    class Dialed {
        var enabled = false
        var inXDay = 0
        fun load(ctx: Context) {
            val spf = Dialed(ctx)
            enabled = spf.isEnabled()
            inXDay = spf.getConfig()
        }
        fun apply(ctx: Context) {
            val spf = Dialed(ctx)
            spf.setEnabled(enabled)
            spf.setConfig(inXDay)
        }
    }
    @Serializable
    class BlockType {
        var type = 0
        fun load(ctx: Context) { type = BlockType(ctx).getType() }
        fun apply(ctx: Context) { BlockType(ctx).setType(type) }
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
            val (stHour_, stMin_) = spf.getStart()
            val (etHour_, etMin_) = spf.getEnd()
            stHour = stHour_
            stMin = stMin_
            etHour = etHour_
            etMin = etMin_
        }
        fun apply(ctx: Context) {
            val spf = OffTime(ctx)
            spf.setEnabled(enabled)
            spf.setStart(stHour, stMin)
            spf.setEnd(etHour, etMin)
        }
    }
    @Serializable
    class RecentApps {
        val list = mutableListOf<String>()
        var inXMin = 0
        fun load(ctx: Context) {
            val spf = RecentApps(ctx)
            list.clear()
            list.addAll(spf.getList())
            inXMin = spf.getConfig()
        }
        fun apply(ctx: Context) {
            val spf = RecentApps(ctx)
            spf.setList(list)
            spf.setConfig(inXMin)
        }
    }
    @Serializable
    abstract class PatternRules {
        val rules = mutableListOf<PatternRule>()

        abstract fun table() : RuleTable
        fun load(ctx: Context) {
            rules.clear()
            rules.addAll(table().listAll(ctx))
        }
        fun apply(ctx: Context) {
            val tbl = table()
            tbl.clearAll(ctx)
            rules.forEach{
                tbl.addRuleWithId(ctx, it)
            }
        }
    }
    @Serializable
    class NumberRules : PatternRules() {
        override fun table(): RuleTable { return NumberRuleTable() }
    }
    @Serializable
    class ContentRules : PatternRules() {
        override fun table(): RuleTable { return ContentRuleTable() }
    }
    @Serializable
    class QuickCopyRules : PatternRules() {
        override fun table(): RuleTable { return QuickCopyRuleTable() }
    }
}

@Serializable
class Configs {
    val global = Config.Global()
    val theme = Config.Theme()
    val language = Config.Language()

    val contacts = Config.Contact()
    val stir = Config.STIR()
    val repeatedCall = Config.RepeatedCall()
    val dialed = Config.Dialed()
    val recentApps = Config.RecentApps()
    val blockType = Config.BlockType()
    val offTime = Config.OffTime()

    val numberRules = Config.NumberRules()
    val contentRules = Config.ContentRules()
    val quickCopyRules = Config.QuickCopyRules()

    fun load(ctx: Context) {
        global.load(ctx)
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
}