package spam.blocker.service.bot

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import spam.blocker.service.checker.CheckContext
import spam.blocker.util.ILogger
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper

// When adding a new IAction type, follow all the steps:
//  - implement it in Actions.kt
//  - add to  `botActions` or `apiActions` below
//  - add to  `botModule` in BotSerializersModule.kt

val botActions = listOf(
    HttpDownload(),
    CallEvent(),
    SmsEvent(),
    CallThrottling(),
    SmsThrottling(),
    CalendarEvent(),
    Ringtone(),
    QuickTile(),
    ImportToSpamDB(),
    CleanupSpamDB(),
    ImportAsRegexRule(),
    FindRules(),
    ModifyRules(),
    ModifyNumber(),
    ReadFile(),
    WriteFile(),
    ParseCSV(),
    ParseXML(),
    RegexExtract(),
    ConvertNumber(),
    CleanupHistory(),
    BackupExport(),
    BackupImport(),
    EnableWorkflow(),
    EnableApp(),
)

val apiActions = listOf(
    InterceptCall(),
    InterceptSms(),
    HttpDownload(),
    ParseQueryResult(),
    FilterSpamResult(),
    ConvertNumber(),
    ImportToSpamDB(),
    CategoryConfig()
)


// A list represents all input/output types, which is used to check whether two items are chainable.
enum class ParamType {
    None,
    String,
    ByteArray,
    RuleList,
    InstantQueryResult,
}

data class ActionContext(
    val scope: CoroutineScope = CoroutineScope(IO),

    // the error reason when it fails
    var logger: ILogger? = null,

    // the output of the previous action
    var lastOutput: Any? = null,

    // for bot only
    var workTag: String? = null,

    // ---- for api only ----
    // Set by the caller before executing the actions, it will be read by Action `ParseIncomingNumber`
    var rawNumber: String? = null,
    // The action `ParseIncomingNumber` will parse the rawNumber and fill these values,
    //  they will be used in `HttpDownload`
    // For the number "+12223334444"
    var cc: String? = null, // "1"
    var domestic: String? = null, // "2223334444"
    var fullNumber: String? = null, // "12223334444"
    var smsContent: String? = null,
    // The spam category, used when reporting.
    //  tagCategory will be converted to realCategory in Action CategoryConfig, and will then be used in http request
    var tagCategory: String? = null,
    var realCategory: String? = null,
    // set by HttpDownload, used by ImportToSpamDB as detailInfo
    var httpUrl: String? = null,
    // The check result by the first api that successfully identified the number,
    //  for Checker usage only, not for Actions
    var racingResult: ApiQueryResult? = null,

    // Save the input data bytes for `ParseQueryResult`, so they can be chained together
    //  to simulate the "priority" of positive/negative identifiers.
    //  (e.g.: add two `ParseQueryResult` actions, one for positive, one for negative)
    var lastParsedQueryData: ByteArray? = null,

    // For altering rules on the fly, e.g. temporarily enable a rule for some ongoing calendar event.
    // Changes happens in memory and won't persist to the configuration.
    var cCtx: CheckContext? = null,
    var isInMemory: Boolean = false,

    // Should mute the ringtone for allowed call?
    var shouldMute: Boolean = false,

)

interface IAction {
    // When it succeeds, it returns: <true, output>
    //   the output will be used as the input `param` for the next Action
    // When it fails, it returns: <false, errorReasonString>
    fun execute(ctx: Context, aCtx: ActionContext): Boolean

    // It returns a list of missing permissions.
    fun requiredPermissions(ctx: Context): List<PermissionWrapper>

    // The display name of this action
    fun label(ctx: Context): String

    // A brief string showing current settings
    @Composable
    fun Summary()

    // Explains what this action does, used in balloon tooltip
    fun tooltip(ctx: Context): String
//    fun onTooltipLinkClick(ctx: Context): Lambda1<String>? = null

    // For checking if two sibling actions are chainable
    fun inputParamType(): List<ParamType>
    fun outputParamType(): List<ParamType>

    // The icon of this action.
    @Composable
    fun Icon()

    // The option dialog for editing this action.
    @Composable
    fun Options()
}

// Trigger types: OnCall/OnSMS/OnCalendarEvents/
interface ITriggerAction : IAction {
    @Composable
    fun TriggerType(modifier: Modifier)
    fun isActivated(): Boolean
}


// Actions that don't require any permission
interface IPermissiveAction : IAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        return listOf()
    }
}

// Actions that require Internet permission, such as HTTP, FTP ...
//interface IInternetAction: IAction {
//    override fun isPermissionGranted(ctx: Context): Boolean {
//        return Permissions.isInternetPermissionGranted(ctx)
//    }
//    override fun askForPermission(ctx: Context) { }
//}

// Actions that require file read/write permissions
interface IFileAction : IAction {
    override fun requiredPermissions(ctx: Context): List<PermissionWrapper> {
        val ret = mutableListOf<PermissionWrapper>()

        if (!Permission.fileRead.isGranted)
            ret.add(PermissionWrapper(Permission.fileRead))
        if (!Permission.fileWrite.isGranted)
            ret.add(PermissionWrapper(Permission.fileWrite))

        return ret
    }
}


val actionsSaver = Saver<SnapshotStateList<IAction>, String>(
    save = { it.serialize() },
    restore = { mutableStateListOf(*it.parseActions().toTypedArray()) }
)

@Composable
fun rememberSaveableActionList(actions: List<IAction>): SnapshotStateList<IAction> {
    return rememberSaveable(saver = actionsSaver) {
        mutableStateListOf(*actions.toTypedArray())
    }
}

fun IAction.clone(): IAction {
    return this.serialize().parseAction()
}

fun IAction.serialize(): String {
    return botJson.encodeToString(PolymorphicSerializer(IAction::class), this)
}

fun String.parseAction(): IAction {
    return botJson.decodeFromString(PolymorphicSerializer(IAction::class), this)
}

fun List<IAction>.serialize(): String {
    return botJson.encodeToString(ListSerializer(PolymorphicSerializer(IAction::class)), this)
}

fun String.parseActions(): List<IAction> {
    if (this.isEmpty())
        return listOf()

    return try {
        botJson.decodeFromString(ListSerializer(PolymorphicSerializer(IAction::class)), this)
    } catch (_: Exception) {
        listOf()
    }
}

// return:
//  null - no need to draw the indicator
//  true - draw green indicator
//  false - draw red indicator
fun isPreviousChainable(curr: IAction, prev: IAction?): Boolean? {
    return if (prev == null) {
        if (curr.inputParamType().contains(ParamType.None))
            null
        else
            false
    } else {
        // They will be chainable if the input/output param list share same type of params
        curr.inputParamType().intersect(prev.outputParamType()).isNotEmpty()
    }
}

fun isNextChainable(curr: IAction, next: IAction?): Boolean? {
    return if (next == null) {
        if (curr.outputParamType().contains(ParamType.None))
            null
        else
            false
    } else {
        curr.outputParamType().intersect(next.inputParamType()).isNotEmpty()
    }
}

// Returns whether there is any error in this chain.
fun List<IAction>.allChainable(): Boolean {
    for (i in this.indices) {
        val curr = this[i]

        val prev: IAction? = if (i == 0) null else this[i - 1]
        val prevOK = isPreviousChainable(curr, prev)
        if (prevOK == false) return false

        val next: IAction? = if (i == this.size - 1) null else this[i + 1]
        val nextOK = isNextChainable(curr, next)
        if (nextOK == false) return false
    }
    return true
}

// Return value:
//  - Boolean, succeeded or failed
fun List<IAction>.executeAll(
    ctx: Context,
    aCtx: ActionContext,
): Boolean {
    val self = this

    // Run until any action fails
    val anyError = runBlocking {
        aCtx.scope.async {
            self.any {
                val succeeded = try {
                    it.execute(ctx, aCtx)
                } catch (_: Exception) {
                    false
                }

                yield()

                !succeeded
            }
        }.await()
    }
//    if (anyError) {
//        aCtx.logger?.error(ctx.getString(R.string.failed))
//    }
    return !anyError
}
