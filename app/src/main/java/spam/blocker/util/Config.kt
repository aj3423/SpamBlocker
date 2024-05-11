package spam.blocker.util

import android.content.Context
import kotlinx.serialization.Serializable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.PatternRule
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RuleTable

class Config {
    @Serializable
    class Global {
        var enabled = false
        fun load(ctx: Context) { enabled = SharedPref(ctx).isGloballyEnabled() }
        fun apply(ctx: Context) { SharedPref(ctx).setGloballyEnabled(enabled) }
    }
    @Serializable
    class Theme {
        var isDark = false
        fun load(ctx: Context) { isDark = SharedPref(ctx).isDarkTheme() }
        fun apply(ctx: Context) { SharedPref(ctx).setDarkTheme(isDark) }
    }
    @Serializable
    class Contact {
        var enabled = false
        var isExcusive = false
        fun load(ctx: Context) {
            val spf = SharedPref(ctx)
            enabled = spf.isContactEnabled()
            isExcusive = spf.isContactExclusive()
        }
        fun apply(ctx: Context) {
            val spf = SharedPref(ctx)
            spf.setContactEnabled(enabled)
            spf.setContactExclusive(isExcusive)
        }
    }
    @Serializable
    class RepeatedCall {
        var enabled = false
        var times = 0
        var inXMin = 0
        fun load(ctx: Context) {
            val spf = SharedPref(ctx)
            enabled = spf.isRepeatedCallEnabled()
            val (tms, inxmin) = spf.getRepeatedConfig()
            times = tms
            inXMin = inxmin
        }
        fun apply(ctx: Context) {
            val spf = SharedPref(ctx)
            spf.setRepeatedCallEnabled(enabled)
            spf.setRepeatedConfig(times, inXMin)
        }
    }
    @Serializable
    class Dialed {
        var enabled = false
        var inXDay = 0
        fun load(ctx: Context) {
            val spf = SharedPref(ctx)
            enabled = spf.isDialedEnabled()
            inXDay = spf.getDialedConfig()
        }
        fun apply(ctx: Context) {
            val spf = SharedPref(ctx)
            spf.setDialedEnabled(enabled)
            spf.setDialedConfig(inXDay)
        }
    }
    @Serializable
    class Silence {
        var enabled = false
        fun load(ctx: Context) { enabled = SharedPref(ctx).isSilenceCallEnabled() }
        fun apply(ctx: Context) { SharedPref(ctx).setSilenceCallEnabled(enabled) }
    }
    @Serializable
    class RecentApps {
        val list = mutableListOf<String>()
        var inXMin = 0
        fun load(ctx: Context) {
            val spf = SharedPref(ctx)
            list.clear()
            list.addAll(spf.getRecentAppList())
            inXMin = spf.getRecentAppConfig()
        }
        fun apply(ctx: Context) {
            val spf = SharedPref(ctx)
            spf.setRecentAppList(list)
            spf.setRecentAppConfig(inXMin)
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
                tbl.addPatternRuleWithId(ctx, it)
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
    val contacts = Config.Contact()
    val repeatedCall = Config.RepeatedCall()
    val dialed = Config.Dialed()
    val recentApps = Config.RecentApps()
    val silence = Config.Silence()
    val numberRules = Config.NumberRules()
    val contentRules = Config.ContentRules()
    val quickCopyRules = Config.QuickCopyRules()

    fun load(ctx: Context) {
        global.load(ctx)
        theme.load(ctx)
        contacts.load(ctx)
        repeatedCall.load(ctx)
        dialed.load(ctx)
        recentApps.load(ctx)
        silence.load(ctx)
        numberRules.load(ctx)
        contentRules.load(ctx)
        quickCopyRules.load(ctx)
    }
    fun apply(ctx: Context) {
        global.apply(ctx)
        theme.apply(ctx)
        contacts.apply(ctx)
        repeatedCall.apply(ctx)
        dialed.apply(ctx)
        recentApps.apply(ctx)
        silence.apply(ctx)
        numberRules.apply(ctx)
        contentRules.apply(ctx)
        quickCopyRules.apply(ctx)
    }
}