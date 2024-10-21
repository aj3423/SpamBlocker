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
import spam.blocker.def.Def
import spam.blocker.util.IPermission
import spam.blocker.util.IntentPermission
import spam.blocker.util.NormalPermission
import spam.blocker.util.Permissions

// When adding a new IAction type, follow all the steps:
//  - implement it in Actions.kt
//  - add to  `defaultActions`
//  - add to  `botModule` in BotSerializersModule.kt

val defaultActions = listOf(
    HttpDownload(),
    ImportToSpamDB(),
    ImportAsRegexRule(),
    ReadFile(),
    WriteFile(),
    ParseCSV(),
    ParseXML(),
    RegexExtract(),
    ConvertNumber(),
    CleanupSpamDB(),
    CleanupHistory(),
    BackupExport(),
    BackupImport(),
)


// A list represents all input/output types, which is used to check whether two items are chainable.
enum class ParamType {
    None,
    String,
    ByteArray,
    RuleList,
}

interface IAction {
    // the return value will be used as the input `arg` for the next Action
    // When it fails, it returns: <false, errorReasonString>
    fun execute(ctx: Context, arg: Any?): Pair<Boolean, Any?> // return <Success, output>

    // Return values:
    // null: no permission needed
    // true or false: is granted or not
    fun missingPermissions(ctx: Context): List<IPermission>

    // A name displayed on the button
    fun label(ctx: Context): String

    // A brief string showing current settings
    fun summary(ctx: Context): String

    // Explains what this action does, used in balloon tooltip
    fun tooltip(ctx: Context): String

    // These two functions are for checking if two sibling actions are chainable
    fun inputParamType(): ParamType
    fun outputParamType(): ParamType


    // the resource icon id
    @Composable
    fun Icon()

    // This will be used on the automation editing popup dialog
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
        if( Permissions.isFileReadPermissionGranted(ctx) &&
                Permissions.isFileWritePermissionGranted(ctx)) {
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
//  - null, all success
//  - String, it failed, this String is the error reason.
fun List<IAction>.executeAll(ctx: Context): String? {
    var lastRet: Any? = null

    // Run until any action fails
    val anyError = this.any {
        val (succeeded, output) = it.execute(ctx, lastRet)
        lastRet = output
        !succeeded
    }
    return if (anyError)
        lastRet as String
    else
        null
}
