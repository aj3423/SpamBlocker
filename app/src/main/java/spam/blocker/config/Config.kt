package spam.blocker.config

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import spam.blocker.G
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.db.ContentRegexTable
import spam.blocker.db.Notification.Channel
import spam.blocker.db.Notification.ChannelTable
import spam.blocker.db.NumberRegexTable
import spam.blocker.db.PushAlertRecord
import spam.blocker.db.PushAlertTable
import spam.blocker.db.QueryApi
import spam.blocker.db.QuickCopyRegexTable
import spam.blocker.db.RegexRule
import spam.blocker.db.RegexTable
import spam.blocker.db.ReportApi
import spam.blocker.db.SpamNumber
import spam.blocker.db.SpamTable
import spam.blocker.util.BotJson
import spam.blocker.util.Notification.createChannel
import spam.blocker.util.Notification.deleteAllChannels
import spam.blocker.util.Permission
import spam.blocker.util.spf
import spam.blocker.util.spf.MeetingAppInfo
import spam.blocker.util.spf.RecentAppInfo
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream


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
    var collapsed = false
    var callEnabled = false
    var smsEnabled = false
    var mmsEnabled = false

    // misc
    var isTestingIconClicked = false

    override fun load(ctx: Context) {
        val spf = spf.Global(ctx)
        enabled = spf.isGloballyEnabled
        collapsed = spf.isCollapsed
        callEnabled = spf.isCallEnabled
        smsEnabled = spf.isSmsEnabled
        mmsEnabled = spf.isMmsEnabled

        isTestingIconClicked = spf.isTestIconClicked
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).apply {
            isGloballyEnabled = enabled
            isCollapsed = collapsed
            isCallEnabled = callEnabled
            isSmsEnabled = smsEnabled
            isMmsEnabled = mmsEnabled

            isTestIconClicked = isTestingIconClicked
        }
    }
}

@Serializable
class HistoryOptions : IConfig {
    var showPassed = true
    var showBlocked = true
    var showIndicator = false
    var showGeoLocation = false
    var forceShowSim = false
    var loggingEnabled = true
    var expiryEnabled = true
    var ttl = -1
    var logSmsContent = false
    var initialSmsRowCount = 1
    var showTimeColor = false
    var timeColors = ""

    override fun load(ctx: Context) {
        val spf = spf.HistoryOptions(ctx)
        showPassed = spf.showPassed
        showBlocked = spf.showBlocked
        showIndicator = spf.showIndicator
        showGeoLocation = spf.showGeoLocation
        forceShowSim = spf.forceShowSim
        loggingEnabled = spf.isLoggingEnabled
        expiryEnabled = spf.isExpiryEnabled
        ttl = spf.ttl
        logSmsContent = spf.isLogSmsContentEnabled
        initialSmsRowCount = spf.initialSmsRowCount
        showTimeColor = spf.showTimeColor
        timeColors = spf.timeColors
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.HistoryOptions(ctx).apply {
            showPassed = me.showPassed
            showBlocked = me.showBlocked
            showIndicator = me.showIndicator
            showGeoLocation = me.showGeoLocation
            forceShowSim = me.forceShowSim
            isLoggingEnabled = me.loggingEnabled
            isExpiryEnabled = me.expiryEnabled
            ttl = me.ttl
            isLogSmsContentEnabled = me.logSmsContent
            initialSmsRowCount = me.initialSmsRowCount
            showTimeColor = me.showTimeColor
            timeColors = me.timeColors
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
        numberCollapsed = spf.isNumberCollapsed
        contentCollapsed = spf.isContentCollapsed
        quickCopyCollapsed = spf.isQuickCopyCollapsed
        maxNoneScrollRows = spf.maxNoneScrollRows
        maxRegexRows = spf.maxRegexRows
        maxDescRows = spf.maxDescRows
        listHeightPercentage = spf.ruleListHeightPercentage
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.RegexOptions(ctx).apply {
            isNumberCollapsed = me.numberCollapsed
            isContentCollapsed = me.contentCollapsed
            isQuickCopyCollapsed = me.quickCopyCollapsed
            maxNoneScrollRows = me.maxNoneScrollRows
            maxRegexRows = me.maxRegexRows
            maxDescRows = me.maxDescRows
            ruleListHeightPercentage = me.listHeightPercentage
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
        enabled = spf.isEnabled
        collapsed = spf.isCollapsed
        duration = spf.duration
        regexStr = spf.regexStr
        regexFlags = spf.regexFlags
        timestamp = spf.timestamp
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.SmsAlert(ctx).apply {
            isEnabled = me.enabled
            isCollapsed = me.collapsed
            duration = me.duration
            regexStr = me.regexStr
            regexFlags = me.regexFlags
            timestamp = me.timestamp
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
        enabled = spf.isEnabled
        collapsed = spf.isCollapsed
        duration = spf.interval
        regexStr = spf.regexStr
        regexFlags = spf.regexFlags
        timestamp = spf.timestamp
        lockscreenProtection = spf.isLockScreenProtectionEnabled
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.SmsBomb(ctx).apply {
            isEnabled = me.enabled
            isCollapsed = me.collapsed
            interval = me.duration
            regexStr = me.regexStr
            regexFlags = me.regexFlags
            timestamp = me.timestamp
            isLockScreenProtectionEnabled = me.lockscreenProtection
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
        enabled = spf.isEnabled
        stirEnabled = spf.isStirEnabled
        collapsed = spf.isCollapsed
        duration = spf.duration
        extraNumbers = spf.getExtraNumbers()
        timestamp = spf.timestamp
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.EmergencySituation(ctx).apply {
            isEnabled = me.enabled
            isStirEnabled = me.stirEnabled
            isCollapsed = me.collapsed
            duration = me.duration
            setExtraNumbers(me.extraNumbers)
            timestamp = me.timestamp
        }
    }
}

@Serializable
class BotOptions : IConfig {
    var listCollapsed = false
    var dynamicTile0Enabled = false

    override fun load(ctx: Context) {
        val spf = spf.BotOptions(ctx)
        listCollapsed = spf.isListCollapsed
        dynamicTile0Enabled = spf.isDynamicTileEnabled(0)
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.BotOptions(ctx).apply {
            isListCollapsed = me.listCollapsed
            setDynamicTileEnabled(0, me.dynamicTile0Enabled)
        }
    }
}

@Serializable
class Theme : IConfig {
    var type = 0
    override fun load(ctx: Context) {
        type = spf.Global(ctx).themeType
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).themeType = type
    }
}

@Serializable
class Language : IConfig {
    var lang = ""
    override fun load(ctx: Context) {
        lang = spf.Global(ctx).language
    }

    override fun apply(ctx: Context) {
        spf.Global(ctx).language = lang
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
        enabled = spf.isEnabled
        isExcusive = spf.isStrict
        permissivePriority = spf.lenientPriority
        strictPriority = spf.strictPriority
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.Contact(ctx).apply {
            isEnabled = me.enabled
            isStrict = me.isExcusive
            lenientPriority = me.permissivePriority
            strictPriority = me.strictPriority
        }
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
        enabled = spf.isEnabled
        includeUnverified = spf.isIncludeUnverified
        strictPriority = spf.priority
    }

    override fun apply(ctx: Context) {
        val spf = spf.Stir(ctx)
        spf.isEnabled = enabled
        spf.isIncludeUnverified = includeUnverified
        spf.priority = strictPriority
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
        enabled = spf.isEnabled
        expiryEnabled = spf.isExpiryEnabled
        priority = spf.priority
        ttl = spf.ttl
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.SpamDB(ctx).apply {
            isEnabled = me.enabled
            isExpiryEnabled = me.expiryEnabled
            priority = me.priority
            ttl = me.ttl
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
        enabled = spf.isEnabled
        times = spf.times
        inXMin = spf.inXMin
    }

    override fun apply(ctx: Context) {
        val spf = spf.RepeatedCall(ctx)
        spf.isEnabled = enabled
        spf.times = times
        spf.inXMin = inXMin
    }
}

@Serializable
class Dialed : IConfig {
    var enabled = false
    var smsEnabled = false
    var inXDay = 0
    override fun load(ctx: Context) {
        val spf = spf.Dialed(ctx)
        enabled = spf.isEnabled
        smsEnabled = spf.isSmsEnabled
        inXDay = spf.days
    }

    override fun apply(ctx: Context) {
        val spf = spf.Dialed(ctx)
        spf.isEnabled = enabled
        spf.isSmsEnabled = smsEnabled
        spf.days = inXDay
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
        warningAcknowledged = spf.isWarningAcknowledged
        enabled = spf.isEnabled
        minDuration = spf.minDuration
        inXDay = spf.days
    }

    override fun apply(ctx: Context) {
        val spf = spf.Answered(ctx)
        spf.isWarningAcknowledged = warningAcknowledged
        spf.isEnabled = enabled
        spf.minDuration = minDuration
        spf.days = inXDay
    }
}

@Serializable
class BlockType : IConfig {
    var type = 0
    var config = ""
    override fun load(ctx: Context) {
        val spf = spf.BlockType(ctx)
        type = spf.type
        config = spf.delay
    }

    override fun apply(ctx: Context) {
        val spf = spf.BlockType(ctx)
        spf.type = type
        spf.delay = config
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
        spamCallChannel = spf.spamCallChannelId
        spamSmsChannel = spf.spamSmsChannelId
        validSmsChannel = spf.validSmsChannelId
        activeSmsChatChannel = spf.smsChatChannelId
        channels.clear()
        channels.addAll(ChannelTable.listAll(ctx))
    }

    override fun apply(ctx: Context) {
        // 1. spf
        spf.Notification(ctx).apply {
            spamCallChannelId = spamCallChannel
            spamSmsChannelId = spamSmsChannel
            validSmsChannelId = validSmsChannel
            smsChatChannelId = activeSmsChatChannel
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

        enabled = spf.isEnabled

        stHour = spf.startHour
        stMin = spf.startMin
        etHour = spf.endHour
        etMin = spf.endMin
    }

    override fun apply(ctx: Context) {
        spf.OffTime(ctx).apply {
            isEnabled = enabled

            startHour = stHour
            startMin = stMin
            endHour = etHour
            endMin = etMin
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
        inXMin = spf.inXMin
    }

    override fun apply(ctx: Context) {
        val spf = spf.RecentApps(ctx)
        spf.setList(list)
        spf.inXMin = inXMin
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
        priority = spf.priority
    }

    override fun apply(ctx: Context) {
        val spf = spf.MeetingMode(ctx)
        spf.setList(list)
        spf.priority = priority
    }
}

@Serializable
abstract class PatternRules : IConfig {
    val rules = mutableListOf<RegexRule>()

    abstract fun table(): RegexTable
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
    override fun table(): RegexTable {
        return NumberRegexTable()
    }
}

@Serializable
class ContentRules : PatternRules() {
    override fun table(): RegexTable {
        return ContentRegexTable()
    }
}

@Serializable
class QuickCopyRules : PatternRules() {
    override fun table(): RegexTable {
        return QuickCopyRegexTable()
    }
}

@Serializable
class ApiQuery : IConfig {
    val apis = mutableListOf<QueryApi>()
    var listCollapsed = false
    var priority = -1

    override fun load(ctx: Context) {
        apis.clear()
        apis.addAll(G.apiQueryVM.table.listAll(ctx).map { it as QueryApi })
        val spf = spf.ApiQueryOptions(ctx)
        listCollapsed = spf.isListCollapsed
        priority = spf.priority
    }

    override fun apply(ctx: Context) {
        val table = G.apiQueryVM.table
        table.clearAll(ctx)
        apis.forEach {
            table.addRecordWithId(ctx, it)
        }
        val spf = spf.ApiQueryOptions(ctx)
        spf.isListCollapsed = listCollapsed
        spf.priority = priority
    }
}
@Serializable
class ApiReport : IConfig {
    val apis = mutableListOf<ReportApi>()
    var listCollapsed = false

    override fun load(ctx: Context) {
        apis.clear()
        apis.addAll(G.apiReportVM.table.listAll(ctx).map { it as ReportApi })
        listCollapsed = spf.ApiReportOptions(ctx).isListCollapsed
    }

    override fun apply(ctx: Context) {
        val table = G.apiReportVM.table
        table.clearAll(ctx)
        apis.forEach {
            table.addRecordWithId(ctx, it)
        }
        spf.ApiReportOptions(ctx).isListCollapsed = listCollapsed
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

    @OptIn(ExperimentalSerializationApi::class)
    fun toByteArray(): ByteArray {
        val me = this
        return ByteArrayOutputStream(128 * 1024).use { baos ->  // optional initial size hint
            GZIPOutputStream(baos).apply {
                BotJson.encodeToStream(serializer(), me, this)
                finish()  // explicit finish (good practice)
            }
            baos.toByteArray()
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun fromByteArray(bytes: ByteArray) : Configs {
//            val newCfg = BotJson.decodeFromString<Configs>(jsonStr)
//            return newCfg

            return ByteArrayInputStream(bytes).use { input ->
                GZIPInputStream(input).use { gzip ->
                    BotJson.decodeFromStream(
                        deserializer = Configs.serializer(),
                        stream = gzip
                    )
                }
            }
        }
    }
}
