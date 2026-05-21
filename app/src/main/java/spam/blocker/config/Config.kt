package spam.blocker.config

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import spam.blocker.G
import spam.blocker.R
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

// ConfigJson is based on BotJson because backup files contain polymorphic bot/action/API objects,
//   they require custom serializing modules that already registered in BotJson.
@OptIn(ExperimentalSerializationApi::class)
private val ConfigJson = Json(BotJson) {
    explicitNulls = false
}

interface IConfig {
    fun load(ctx: Context) // load current settings into this object before saving to a backup file
    fun apply(ctx: Context) // import from a backup file and override the current settings.
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
    var showCarrier = false
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
        showCarrier = spf.showCarrier
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
            showCarrier = me.showCarrier
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
    var colorMap: MutableMap<String, Int> = mutableMapOf()

    override fun load(ctx: Context) {
        spf.Palette(ctx).allColors.forEach {
            colorMap[it.key] = it.state.value.toArgb()
        }
    }

    override fun apply(ctx: Context) {
        // Iterate through spf.allColors
        spf.Palette(ctx).allColors.forEach { delegate ->
            // Apply new color if it exists in the config
            colorMap[delegate.key]?.let {
                delegate.update(Color(it))
            }
        }
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
    var always = false
    var inXDay = 0
    override fun load(ctx: Context) {
        val spf = spf.Dialed(ctx)
        enabled = spf.isEnabled
        smsEnabled = spf.isSmsEnabled
        always = spf.always
        inXDay = spf.days
    }

    override fun apply(ctx: Context) {
        val spf = spf.Dialed(ctx)
        spf.isEnabled = enabled
        spf.isSmsEnabled = smsEnabled
        spf.always = always
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
class CallerID : IConfig {
    var enabled = false
    var x = 0
    var y = 0
    var bgColor: Int = 0
    var template = ""
    override fun load(ctx: Context) {
        val spf = spf.CallerID(ctx)
        enabled = spf.isEnabled
        x = spf.x
        y = spf.y
        bgColor = spf.bgColor
        template = spf.template
    }

    override fun apply(ctx: Context) {
        val spf = spf.CallerID(ctx)
        spf.isEnabled = enabled
        spf.x = x
        spf.y = y
        spf.bgColor = bgColor
        spf.template = template
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
class OAuth : IConfig {
    var phoneBlockToken = ""
    override fun load(ctx: Context) {
        val spf = spf.OAuth(ctx)

        phoneBlockToken = spf.phoneBlockToken
    }

    override fun apply(ctx: Context) {
        val me = this
        spf.OAuth(ctx).apply {
            phoneBlockToken = me.phoneBlockToken
        }
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

enum class Category(val labelId: Int) {
    OTHERS(R.string.others),
    REGEX_RULES(R.string.regex_settings),
    APIS(R.string.api_settings),
    WORKFLOWS(R.string.workflows),
    LANGUAGE(R.string.language),
    THEME(R.string.theme),
    SPAM_NUMBERS(R.string.database)
}

@Serializable
data class CategorySelection(
    val all: Set<Category> = Category.entries
        .filter { it != Category.SPAM_NUMBERS }
        .toSet()
) {
    fun isSelected(category: Category): Boolean = category in all

    // Returns a new state with the category added to the selection.
    fun select(category: Category): CategorySelection {
        return copy(all = all + category)
    }

    // Returns a new state with the category removed from the selection.
    fun unselect(category: Category): CategorySelection {
        return copy(all = all - category)
    }

    fun toggle(category: Category): CategorySelection {
        return if (isSelected(category)) unselect(category) else select(category)
    }

    fun contains(category: Category): Boolean {
        return category in all
    }

    fun negate(): CategorySelection {
        return CategorySelection(allUnselected().toSet())
    }

    fun allSelected(): List<Category> = all.toList()

    fun allUnselected(): List<Category> = Category.entries.filter { it !in all }
}
val defaultCategorySelection by lazy {
    CategorySelection()
}
val emptyCategorySelection by lazy {
    CategorySelection(emptySet())
}


@Serializable
class Configs {
    var categories : CategorySelection? = null

    var global : Global? = null
    var historyOptions : HistoryOptions? = null
    var regexOptions : RegexOptions? = null
    var botOptions : BotOptions? = null
    var language : Language? = null
    var theme : Theme? = null

    var contacts : Contact? = null
    var stir : STIR? = null
    var spamDB : SpamDB? = null
    var repeatedCall : RepeatedCall? = null
    var dialed : Dialed? = null
    var answered : Answered? = null
    var recentApps : RecentApps? = null
    var meetingMode : MeetingMode? = null
    var blockType : BlockType? = null
    var callerID : CallerID? = null
    var notification : Notification? = null
    var offTime : OffTime? = null

    var numberRules : NumberRules? = null
    var contentRules : ContentRules? = null
    var quickCopyRules : QuickCopyRules? = null
    var pushAlert : PushAlert? = null
    var smsAlert : SmsAlert? = null
    var emergency : EmergencySituation? = null
    var smsBomb : SmsBomb? = null

    var apiQuery : ApiQuery? = null
    var apiReport : ApiReport? = null
    var bots : Bots? = null

    var spamNumbers : SpamNumbers? = null

    var oauth : OAuth? = null

    var permissions : Permissions? = null

    // Read all settings from SharedPref/Database into this object, for saving to file.
    fun load(ctx: Context, categories: CategorySelection) {
        this.categories = categories

        if (categories.isSelected(Category.OTHERS)) {
            global = Global().also { it.load(ctx) }
            historyOptions = HistoryOptions().also { it.load(ctx) }
            contacts = Contact().also { it.load(ctx) }
            stir = STIR().also { it.load(ctx) }
            spamDB = SpamDB().also { it.load(ctx) }
            repeatedCall = RepeatedCall().also { it.load(ctx) }
            dialed = Dialed().also { it.load(ctx) }
            answered = Answered().also { it.load(ctx) }
            offTime = OffTime().also { it.load(ctx) }
            emergency = EmergencySituation().also { it.load(ctx) }
            recentApps = RecentApps().also { it.load(ctx) }
            meetingMode = MeetingMode().also { it.load(ctx) }
            blockType = BlockType().also { it.load(ctx) }
            notification = Notification().also { it.load(ctx) }
            callerID = CallerID().also { it.load(ctx) }
            oauth = OAuth().also { it.load(ctx) }
            permissions = Permissions().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.REGEX_RULES)) {
            regexOptions = RegexOptions().also { it.load(ctx) }
            numberRules = NumberRules().also { it.load(ctx) }
            contentRules = ContentRules().also { it.load(ctx) }
            quickCopyRules = QuickCopyRules().also { it.load(ctx) }
            pushAlert = PushAlert().also { it.load(ctx) }
            smsAlert = SmsAlert().also { it.load(ctx) }
            smsBomb = SmsBomb().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.APIS)) {
            apiQuery = ApiQuery().also { it.load(ctx) }
            apiReport = ApiReport().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.WORKFLOWS)) {
            botOptions = BotOptions().also { it.load(ctx) }
            bots = Bots().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.LANGUAGE)) {
            language = Language().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.THEME)) {
            theme = Theme().also { it.load(ctx) }
        }
        if (categories.isSelected(Category.SPAM_NUMBERS)) {
            spamNumbers = SpamNumbers().also { it.load(ctx) }
        }
    }

    // This object has been full filled, apply the values to SharedPref/Database
    fun apply(ctx: Context, categories: CategorySelection) {
        if (categories.isSelected(Category.OTHERS)) {
            global?.apply(ctx)
            historyOptions?.apply(ctx)
            contacts?.apply(ctx)
            stir?.apply(ctx)
            spamDB?.apply(ctx)
            repeatedCall?.apply(ctx)
            dialed?.apply(ctx)
            answered?.apply(ctx)
            offTime?.apply(ctx)
            emergency?.apply(ctx)
            recentApps?.apply(ctx)
            meetingMode?.apply(ctx)
            blockType?.apply(ctx)
            notification?.apply(ctx)
            callerID?.apply(ctx)
            oauth?.apply(ctx)
            permissions?.apply(ctx)
        }
        if (categories.isSelected(Category.REGEX_RULES)) {
            regexOptions?.apply(ctx)

            numberRules?.apply(ctx)
            contentRules?.apply(ctx)
            quickCopyRules?.apply(ctx)
            pushAlert?.apply(ctx)
            smsAlert?.apply(ctx)
            smsBomb?.apply(ctx)
        }
        if (categories.isSelected(Category.APIS)) {
            apiQuery?.apply(ctx)
            apiReport?.apply(ctx)
        }
        if (categories.isSelected(Category.WORKFLOWS)) {
            botOptions?.apply(ctx)
            bots?.apply(ctx)
        }
        if (categories.isSelected(Category.LANGUAGE)) {
            language?.apply(ctx)
        }
        if (categories.isSelected(Category.THEME)) {
            theme?.apply(ctx)
        }
        if (categories.isSelected(Category.SPAM_NUMBERS)) {
            spamNumbers?.apply(ctx)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun toByteArray(): ByteArray {
        val me = this
        return ByteArrayOutputStream(128 * 1024).use { baos ->  // optional initial size hint
            GZIPOutputStream(baos).apply {
                ConfigJson.encodeToStream(serializer(), me, this)
                finish()  // explicit finish (good practice)
            }
            baos.toByteArray()
        }
    }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        fun fromByteArray(bytes: ByteArray) : Configs {
            return ByteArrayInputStream(bytes).use { input ->
                GZIPInputStream(input).use { gzip ->
                    ConfigJson.decodeFromStream(
                        deserializer = Configs.serializer(),
                        stream = gzip
                    )
                }
            }
        }
    }
}
