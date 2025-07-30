package spam.blocker.config

import android.content.Context
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import spam.blocker.G
import spam.blocker.db.Api
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.ContentRuleTable
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.NumberRuleTable
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.db.QuickCopyRuleTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RuleTable
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.service.bot.botJson
import spam.blocker.util.Notification.createChannel
import spam.blocker.util.Notification.deleteAllChannels
import spam.blocker.util.Permission
import spam.blocker.util.spf
import spam.blocker.util.spf.MeetingAppInfo
import spam.blocker.util.spf.RecentAppInfo


interface IConfig {
    fun load(ctx: Context)
    fun apply(ctx: Context)
}
/*
  These default values only works when upgrading from an old version that does not
    have this attribute yet.
 */
@Serializable
class Global : IConfig {
    var enabled = false
    var callEnabled = false
    var smsEnabled = false
    var mmsEnabled = false

    // misc
    var isTestingIconClicked = false

    override fun load(ctx: Context) {
        val spf = spf.Global(ctx)
        enabled = spf.isGloballyEnabled()
        callEnabled = spf.isCallEnabled()
        smsEnabled = spf.isSmsEnabled()
        mmsEnabled = spf.isMmsEnabled()

        isTestingIconClicked = spf.isTestingIconClicked()
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).apply {
            setGloballyEnabled(enabled)
            setCallEnabled(callEnabled)
            setSmsEnabled(smsEnabled)
            setMmsEnabled(mmsEnabled)

            setTestingIconClicked(isTestingIconClicked)
        }
    }
}

@Serializable
class HistoryOptions : IConfig {
    var showPassed = true
    var showBlocked = true
    var showIndicator = false
    var loggingEnabled = true
    var expiryEnabled = true
    var ttl = -1
    var logSmsContent = false
    var initialSmsRowCount = 1

    override fun load(ctx: Context) {
        val spf = spf.HistoryOptions(ctx)
        showPassed = spf.getShowPassed()
        showBlocked = spf.getShowBlocked()
        showIndicator = spf.getShowIndicator()
        loggingEnabled = spf.isLoggingEnabled()
        expiryEnabled = spf.isExpiryEnabled()
        ttl = spf.getTTL()
        logSmsContent = spf.isLogSmsContentEnabled()
        initialSmsRowCount = spf.getInitialSmsRowCount()
    }

    override fun apply(ctx: Context) {
        spf.HistoryOptions(ctx).apply {
            setShowPassed(showPassed)
            setShowBlocked(showBlocked)
            setShowIndicator(showIndicator)
            setLoggingEnabled(loggingEnabled)
            setExpiryEnabled(expiryEnabled)
            setTTL(ttl)
            setLogSmsContentEnabled(logSmsContent)
            setInitialSmsRowCount(initialSmsRowCount)
        }
    }
}

@Serializable
class RegexOptions : IConfig {
    var numberCollapsed = false
    var contentCollapsed = false
    var quickCopyCollapsed = false
    var maxNoneScrollRows = 10
    var maxRegexRows = 3
    var maxDescRows = 2
    var listHeightPercentage = 60

    override fun load(ctx: Context) {
        val spf = spf.RegexOptions(ctx)
        numberCollapsed = spf.isNumberCollapsed()
        contentCollapsed = spf.isContentCollapsed()
        quickCopyCollapsed = spf.isQuickCopyCollapsed()
        maxNoneScrollRows = spf.getMaxNoneScrollRows()
        maxRegexRows = spf.getMaxRegexRows()
        maxDescRows = spf.getMaxDescRows()
        listHeightPercentage = spf.getRuleListHeightPercentage()
    }

    override fun apply(ctx: Context) {
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
 class PushAlert : IConfig {
    val rules = mutableListOf<PushAlertRecord>()

    override fun load(ctx: Context) {
        rules.clear()
        rules.addAll(PushAlertTable.listAll(ctx))
    }

    override fun apply(ctx: Context) {
        val tbl = PushAlertTable
        tbl.clearAll(ctx)
        rules.forEach {
            tbl.addWithId(ctx, it)
        }
    }
}

@Serializable
@SerialName("CallAlert") // for historical reason, previously it's named "CallAlert"
class SmsAlert : IConfig {
    var enabled = false
    var collapsed = false
    var duration = 0
    var regexStr = ""
    var regexFlags = 0
    var timestamp: Long = 0

    override fun load(ctx: Context) {
        val spf = spf.SmsAlert(ctx)
        enabled = spf.isEnabled()
        collapsed = spf.isCollapsed()
        duration = spf.getDuration()
        regexStr = spf.getRegexStr()
        regexFlags = spf.getRegexFlags()
        timestamp = spf.getTimestamp()
    }

    override fun apply(ctx: Context) {
        spf.SmsAlert(ctx).apply {
            setEnabled(enabled)
            setCollapsed(collapsed)
            setDuration(duration)
            setRegexStr(regexStr)
            setRegexFlags(regexFlags)
            setTimestamp(timestamp)
        }
    }
}
@Serializable
class SmsBomb : IConfig {
    var enabled = false
    var collapsed = false
    var duration = 0
    var regexStr = ""
    var regexFlags = 0
    var timestamp: Long = 0
    var lockscreenProtection = true

    override fun load(ctx: Context) {
        val spf = spf.SmsBomb(ctx)
        enabled = spf.isEnabled()
        collapsed = spf.isCollapsed()
        duration = spf.getInterval()
        regexStr = spf.getRegexStr()
        regexFlags = spf.getRegexFlags()
        timestamp = spf.getTimestamp()
        lockscreenProtection = spf.isLockScreenProtectionEnabled()
    }

    override fun apply(ctx: Context) {
        spf.SmsBomb(ctx).apply {
            setEnabled(enabled)
            setCollapsed(collapsed)
            setInterval(duration)
            setRegexStr(regexStr)
            setRegexFlags(regexFlags)
            setTimestamp(timestamp)
            setLockScreenProtectionEnabled(lockscreenProtection)
        }
    }
}

@Serializable
class EmergencySituation : IConfig {
    var enabled = false
    var stirEnabled = false
    var collapsed = false
    var duration = 0
    var extraNumbers = listOf<String>()
    var timestamp: Long = 0

    override fun load(ctx: Context) {
        val spf = spf.EmergencySituation(ctx)
        enabled = spf.isEnabled()
        stirEnabled = spf.isStirEnabled()
        collapsed = spf.isCollapsed()
        duration = spf.getDuration()
        extraNumbers = spf.getExtraNumbers()
        timestamp = spf.getTimestamp()
    }

    override fun apply(ctx: Context) {
        spf.EmergencySituation(ctx).apply {
            setEnabled(enabled)
            setStirEnabled(stirEnabled)
            setCollapsed(collapsed)
            setDuration(duration)
            setExtraNumbers(extraNumbers)
            setTimestamp(timestamp)
        }
    }
}

@Serializable
class BotOptions : IConfig {
    var listCollapsed = false

    override fun load(ctx: Context) {
        val spf = spf.BotOptions(ctx)
        listCollapsed = spf.isListCollapsed()
    }

    override fun apply(ctx: Context) {
        spf.BotOptions(ctx).apply {
            setListCollapsed(listCollapsed)
        }
    }
}

@Serializable
class Theme : IConfig {
    var type = 0
    override fun load(ctx: Context) {
        type = spf.Global(ctx).getThemeType()
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).setThemeType(type)
    }
}

@Serializable
class Language : IConfig {
    var lang = ""
    override fun load(ctx: Context) {
        lang = spf.Global(ctx).getLanguage()
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).setLanguage(lang)
    }
}

@Serializable
class Contact : IConfig {
    var enabled = false
    var isExcusive = false
    var permissivePriority = 0
    var strictPriority = 0
    override fun load(ctx: Context) {
        val spf = spf.Contact(ctx)
        enabled = spf.isEnabled()
        isExcusive = spf.isStrict()
        permissivePriority = spf.getLenientPriority()
        strictPriority = spf.getStrictPriority()
    }

    override fun apply(ctx: Context) {
        val spf = spf.Contact(ctx)
        spf.setEnabled(enabled)
        spf.setStrict(isExcusive)
        spf.setLenientPriority(permissivePriority)
        spf.setStrictPriority(strictPriority)
    }
}


@Serializable
class STIR : IConfig {
    var enabled = false

    // The @Transient annotation ensures isExcusive is not serialized in new data,
    //   but it can still be deserialized from old backups.
    @Transient var isExcusive: Boolean? = null

    var includeUnverified = false
    var strictPriority = 0

    // v4.15 removed `isExcusive`, use this class for history compatibility.
    // Disable Unverified if `isExcusive` exists and its value is 0 (as the old Lenient).
    // (Remove this `init` and `isExcusive` after 2027-01-01)
    init {
        if (isExcusive == false) {
            includeUnverified = false
        }
        isExcusive = null // Clear isExcusive after migration
    }

    override fun load(ctx: Context) {
        val spf = spf.Stir(ctx)
        enabled = spf.isEnabled()
        includeUnverified = spf.isIncludeUnverified()
        strictPriority = spf.getPriority()
    }

    override fun apply(ctx: Context) {
        val spf = spf.Stir(ctx)
        spf.setEnabled(enabled)
        spf.setIncludeUnverified(includeUnverified)
        spf.setPriority(strictPriority)
    }
}
@Serializable
class SpamDB : IConfig {
    var enabled = false
    var expiryEnabled = true
    var priority = 0
    var ttl = 90

    override fun load(ctx: Context) {
        val spf = spf.SpamDB(ctx)
        enabled = spf.isEnabled()
        expiryEnabled = spf.isExpiryEnabled()
        priority = spf.getPriority()
        ttl = spf.getTTL()
    }

    override fun apply(ctx: Context) {
        spf.SpamDB(ctx).apply {
            setEnabled(enabled)
            setExpiryEnabled(expiryEnabled)
            setPriority(priority)
            setTTL(ttl)
        }
    }
}

@Serializable
class RepeatedCall : IConfig {
    var enabled = false
    var times = 0
    var inXMin = 0
    override fun load(ctx: Context) {
        val spf = spf.RepeatedCall(ctx)
        enabled = spf.isEnabled()
        times = spf.getTimes()
        inXMin = spf.getInXMin()
    }

    override fun apply(ctx: Context) {
        val spf = spf.RepeatedCall(ctx)
        spf.setEnabled(enabled)
        spf.setTimes(times)
        spf.setInXMin(inXMin)
    }
}

@Serializable
class Dialed : IConfig {
    var enabled = false
    var smsEnabled = false
    var inXDay = 0
    override fun load(ctx: Context) {
        val spf = spf.Dialed(ctx)
        enabled = spf.isEnabled()
        smsEnabled = spf.isSmsEnabled()
        inXDay = spf.getDays()
    }

    override fun apply(ctx: Context) {
        val spf = spf.Dialed(ctx)
        spf.setEnabled(enabled)
        spf.setSmsEnabled(smsEnabled)
        spf.setDays(inXDay)
    }
}

@Serializable
class Answered : IConfig {
    var warningAcknowledged = false
    var enabled = false
    var minDuration = 0
    var inXDay = 0
    override fun load(ctx: Context) {
        val spf = spf.Answered(ctx)
        warningAcknowledged = spf.isWarningAcknowledged()
        enabled = spf.isEnabled()
        minDuration = spf.getMinDuration()
        inXDay = spf.getDays()
    }

    override fun apply(ctx: Context) {
        val spf = spf.Answered(ctx)
        spf.setWarningAcknowledged(warningAcknowledged)
        spf.setEnabled(enabled)
        spf.setMinDuration(minDuration)
        spf.setDays(inXDay)
    }
}

@Serializable
class BlockType : IConfig {
    var type = 0
    var config = ""
    override fun load(ctx: Context) {
        val spf = spf.BlockType(ctx)
        type = spf.getType()
        config = spf.getConfig()
    }

    override fun apply(ctx: Context) {
        val spf = spf.BlockType(ctx)
        spf.setType(type)
        spf.setConfig(config)
    }
}



@Serializable
class Notification : IConfig {
    var spamCallChannel = ""
    var spamSmsChannel = ""
    var validSmsChannel = ""
    var activeSmsChatChannel = ""

    val channels = mutableListOf<Channel>()

    override fun load(ctx: Context) {
        val spf = spf.Notification(ctx)
        spamCallChannel = spf.getSpamCallChannelId()
        spamSmsChannel = spf.getSpamSmsChannelId()
        validSmsChannel = spf.getValidSmsChannelId()
        activeSmsChatChannel = spf.getActiveSmsChatChannelId()
        channels.clear()
        channels.addAll(ChannelTable.listAll(ctx))
    }

    override fun apply(ctx: Context) {
        // 1. spf
        spf.Notification(ctx).apply {
            setSpamCallChannelId(spamCallChannel)
            setSpamSmsChannelId(spamSmsChannel)
            setValidSmsChannelId(validSmsChannel)
            setActiveSmsChatChannelId(activeSmsChatChannel)
        }
        // 2. Table and System channels
        ChannelTable.clearAll(ctx)
        deleteAllChannels(ctx)
        channels.forEach {
            ChannelTable.add(ctx, it)
            createChannel(ctx, it)
        }
    }
}

@Serializable
class OffTime : IConfig {
    var enabled = false
    var stHour = 0
    var stMin = 0
    var etHour = 0
    var etMin = 0
    override fun load(ctx: Context) {
        val spf = spf.OffTime(ctx)

        enabled = spf.isEnabled()

        stHour = spf.getStartHour()
        stMin = spf.getStartMin()
        etHour = spf.getEndHour()
        etMin = spf.getEndMin()
    }

    override fun apply(ctx: Context) {
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
class RecentApps : IConfig {
    val list = mutableListOf<RecentAppInfo>() // [pkg.a, pkg.b@20, pkg.c]
    var inXMin = 0
    override fun load(ctx: Context) {
        val spf = spf.RecentApps(ctx)
        list.clear()
        list.addAll(spf.getList())
        inXMin = spf.getInXMin()
    }

    override fun apply(ctx: Context) {
        val spf = spf.RecentApps(ctx)
        spf.setList(list)
        spf.setInXMin(inXMin)
    }
}

@Serializable
class MeetingMode : IConfig {
    val list = mutableListOf<MeetingAppInfo>() // [pkg.a, pkg.b@20, pkg.c]
    var priority = 20
    override fun load(ctx: Context) {
        val spf = spf.MeetingMode(ctx)
        list.clear()
        list.addAll(spf.getList())
        priority = spf.getPriority()
    }

    override fun apply(ctx: Context) {
        val spf = spf.MeetingMode(ctx)
        spf.setList(list)
        spf.setPriority(priority)
    }
}

@Serializable
abstract class PatternRules : IConfig {
    val rules = mutableListOf<RegexRule>()

    abstract fun table(): RuleTable
    override fun load(ctx: Context) {
        rules.clear()
        rules.addAll(table().listAll(ctx))
    }

    override fun apply(ctx: Context) {
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
class ApiQuery : IConfig {
    val apis = mutableListOf<Api>()
    var listCollapsed = false
    var priority = -1

    override fun load(ctx: Context) {
        apis.clear()
        apis.addAll(G.apiQueryVM.table.listAll(ctx))
        val spf = spf.ApiQueryOptions(ctx)
        listCollapsed = spf.isListCollapsed()
        priority = spf.getPriority()
    }

    override fun apply(ctx: Context) {
        val table = G.apiQueryVM.table
        table.clearAll(ctx)
        apis.forEach {
            table.addRecordWithId(ctx, it)
        }
        spf.ApiQueryOptions(ctx).apply {
            setListCollapsed(listCollapsed)
            setPriority(priority)
        }
    }
}
@Serializable
class ApiReport : IConfig {
    val apis = mutableListOf<Api>()
    var listCollapsed = false

    override fun load(ctx: Context) {
        apis.clear()
        apis.addAll(G.apiReportVM.table.listAll(ctx))
        listCollapsed = spf.ApiReportOptions(ctx).isListCollapsed()
    }

    override fun apply(ctx: Context) {
        val table = G.apiReportVM.table
        table.clearAll(ctx)
        apis.forEach {
            table.addRecordWithId(ctx, it)
        }
        spf.ApiReportOptions(ctx).setListCollapsed(listCollapsed)
    }
}

@Serializable
class Bots : IConfig {
    val bots = mutableListOf<Bot>()

    override fun load(ctx: Context) {
        bots.clear()
        bots.addAll(BotTable.listAll(ctx))
    }

    override fun apply(ctx: Context) {
        BotTable.clearAll(ctx)
        bots.forEach {
            BotTable.addRecordWithId(ctx, it)
        }
    }
}
@Serializable
class SpamNumbers : IConfig {
    val numbers = mutableListOf<SpamNumber>()

    override fun load(ctx: Context) {
        numbers.clear()
        numbers.addAll(SpamTable.listAll(ctx))
    }

    override fun apply(ctx: Context) {
        SpamTable.clearAll(ctx)
        SpamTable.addAll(ctx, numbers)
    }
}

@Serializable
class Permissions : IConfig {
    var allEnabledNames = ""

    override fun load(ctx: Context) {
        allEnabledNames = Permission.allEnabled()
            .joinToString(",") { it::class.java.simpleName.substringAfterLast('$') }
    }

    override fun apply(ctx: Context) { }
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
    val answered = Answered()
    val recentApps = RecentApps()
    val meetingMode = MeetingMode()
    val blockType = BlockType()
    val notification = Notification()
    val offTime = OffTime()

    val numberRules = NumberRules()
    val contentRules = ContentRules()
    val quickCopyRules = QuickCopyRules()
    val pushAlert = PushAlert()
    val smsAlert = SmsAlert()
    val emergency = EmergencySituation()
    val smsBomb = SmsBomb()

    val apiQuery = ApiQuery()
    val apiReport = ApiReport()
    val bots = Bots()

    val spamNumbers = SpamNumbers()

    val permissions = Permissions()

    fun all(includeSpamDB: Boolean): List<IConfig> {
        val ret = mutableListOf(
            global,
            historyOptions,
            regexOptions,
            botOptions,
            theme,
            language,

            contacts,
            stir,
            spamDB,
            repeatedCall,
            dialed,
            answered,
            recentApps,
            meetingMode,
            blockType,
            notification,
            offTime,

            numberRules,
            contentRules,
            quickCopyRules,
            pushAlert,
            smsAlert,
            emergency,
            smsBomb,

            apiQuery,
            apiReport,
            bots,

            permissions,
        )
        if (includeSpamDB)
            ret += spamNumbers

        return ret
    }
    // Read all settings from SharedPref/Database to this object, preparing for saving to file.
    fun load(ctx: Context, includeSpamDB: Boolean = true) {
        all(includeSpamDB).forEach { it.load(ctx) }
    }

    // This object has been full filled, apply the values to SharedPref/Database
    fun apply(ctx: Context, includeSpamDB: Boolean = true) {
        all(includeSpamDB).forEach { it.apply(ctx) }
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
