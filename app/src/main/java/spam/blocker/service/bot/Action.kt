package spam.blocker.service.bot

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.ListSerializer
import spam.blocker.R
import spam.blocker.def.Def
import spam.blocker.util.ILogger
import spam.blocker.util.IPermission
import spam.blocker.util.IntentPermission
import spam.blocker.util.NormalPermission
import spam.blocker.util.Permissions

// When adding a new IAction type, follow all the steps:
//  - implement it in Actions.kt
//  - add to  `botActions` / `apiActions`
//  - add to  `botModule` in BotSerializersModule.kt

val botActions = listOf(
    HttpDownload(),
    ImportToSpamDB(),
    CleanupSpamDB(),
    ImportAsRegexRule(),
    FindRules(),
    ModifyRules(),
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
    ParseIncomingNumber(),
    HttpDownload(),
    ParseQueryResult(),
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
    // the error reason when it fails
    var logger: ILogger? = null,

    // the output of the previous action
    var lastOutput: Any? = null,

    // for bot only
    var workTag: String? = null,

    // for api only
    // Set by the caller before executing the actions, it will be read by Action `ParseIncomingNumber`
    var rawNumber: String? = null,
    // The action `ParseIncomingNumber` will parse the rawNumber and fill these values,
    //  they will be used in `HttpDownload`
    // For the number "+12223334444"
    var cc: String? = null, // "1"
    var domestic: String? = null, // "2223334444"
    var fullNumber: String? = null, // "12223334444"
)

interface IAction {
    // When it succeeds, it returns: <true, output>
    //   the output will be used as the input `param` for the next Action
    // When it fails, it returns: <false, errorReasonString>
    fun execute(ctx: Context, aCtx: ActionContext): Boolean

    // It returns a list of missing permissions.
    fun missingPermissions(ctx: Context): List<IPermission>

    // The display name of this action
    fun label(ctx: Context): String

    // A brief string showing current settings
    @Composable
    fun Summary()

    // Explains what this action does, used in balloon tooltip
    fun tooltip(ctx: Context): String

    // For checking if two sibling actions are chainable
    fun inputParamType(): ParamType
    fun outputParamType(): ParamType

    // The icon of this action.
    @Composable
    fun Icon()

    // The option dialog for editing this action.
    @Composable
    fun Options()
}

// Actions that don't require any permission
interface IPermissiveAction : IAction {
    override fun missingPermissions(ctx: Context): List<IPermission> {
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
    override fun missingPermissions(ctx: Context): List<IPermission> {
        if (Permissions.isFileReadPermissionGranted(ctx) &&
            Permissions.isFileWritePermissionGranted(ctx)
        ) {
            return listOf()
        } else {
            return if (Build.VERSION.SDK_INT == Def.ANDROID_10) {
                listOf(
                    NormalPermission(Manifest.permission.READ_EXTERNAL_STORAGE),
                    NormalPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                )
            } else {
                listOf(
                    IntentPermission(
                        intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .apply {
                                data = Uri.fromParts("package", ctx.packageName, null)
                            }
                    ) {
                        Environment.isExternalStorageManager()
                    }
                )
            }
        }
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
        if (curr.inputParamType() == ParamType.None)
            null
        else
            false
    } else {
        curr.inputParamType() == prev.outputParamType()
    }
}

fun isNextChainable(curr: IAction, next: IAction?): Boolean? {
    return if (next == null) {
        if (curr.outputParamType() == ParamType.None)
            null
        else
            false
    } else {
        curr.outputParamType() == next.inputParamType()
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
    // Run until any action fails
    val anyError = this.any {
        val succeeded = it.execute(ctx, aCtx)

        !succeeded
    }
    if (anyError) {
        aCtx.logger?.error(ctx.getString(R.string.failed))
    }
    return !anyError
}
