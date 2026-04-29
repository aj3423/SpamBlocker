package spam.blocker.ui.setting.misc

import android.net.Uri
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.db.Bot
import spam.blocker.db.SpamTable
import spam.blocker.service.bot.FileAction
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.FileChooser
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.InitFile
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LongPressButton
import spam.blocker.ui.widgets.MIME_GZ
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.A
import spam.blocker.util.FileUtils.readDataFromUri
import spam.blocker.util.FileUtils.writeDataToUri
import spam.blocker.util.Launcher
import spam.blocker.util.Permission
import spam.blocker.util.PermissionType
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.formatAnnotated
import spam.blocker.util.hasFolderAccess
import spam.blocker.util.logi
import spam.blocker.util.toFolderDisplayName
import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val Backup_Last_Dir_Tag = "backup_last_dir_tag"

fun missingBotSafUris(bots: List<Bot>) : List<Uri> {
    return bots.flatMap { bot ->
        bot.actions
            .filterIsInstance<FileAction>() // Only keep FileActions
            .mapNotNull { it.uriStr?.toUri() } // Convert to Uri, skip if null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportButton() {
    val C = G.palette
    val ctx = LocalContext.current
    val coroutine = rememberCoroutineScope()

    // Show a dialog for exporting database numbers, it can take long
    val progressTrigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = progressTrigger,
        // the dialog cannot be dismissed by tapping around, it will only disappear when the export is done
        manuallyDismissable = false
    ) {
        val total = remember { SpamTable.count(ctx) }
        val estimate = remember { total/100_000 } // 100k numbers per second

        Text(Str(R.string.exporting_database).formatAnnotated(
            "$total".A(C.infoBlue), "$estimate".A(C.infoBlue)
        ))
    }

    // Show a system file chooser

    fun chooseExportFile(includeSpamDB: Boolean) {
        // prepare file name
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
        val ymd = LocalDate.now().format(formatter)
        val fn = "SpamBlocker.${ymd}${if (includeSpamDB) ".db" else ""}.gz"

        if (includeSpamDB) progressTrigger.value = true

        // prepare file content
        val curr = Configs()
        curr.load(ctx, includeSpamDB)
        val compressed = curr.toByteArray()

        FileChooser.popupWrite(
            init = InitFile(
                filename = fn,
                mimeType = MIME_GZ,
                rememberDirTag = Backup_Last_Dir_Tag,
            ),
            onResult = { uri ->
                uri?.let {
                    if (includeSpamDB) progressTrigger.value = false

                    writeDataToUri(ctx, uri, compressed)
                }
            }
        )
    }

    DropdownWrapper(
        items = listOf(
            LabelItem(
                label = Str(R.string.include_spam_db)
            ) {
                coroutine.launch(IO) {
                    chooseExportFile(includeSpamDB = true)
                }
            }
        )
    ) { expanded ->
        LongPressButton(
            label = Str(R.string.export),
            color = C.teal200,
            onClick = {
                coroutine.launch(IO) {
                    chooseExportFile(includeSpamDB = false)
                }
            },
            onLongClick = {
                expanded.value = true
            }
        )
    }
}

@Composable
fun ImportButton() {
    val ctx = LocalContext.current
    val C = G.palette

    var succeeded by remember { mutableStateOf(false) }
    var errorStr by remember { mutableStateOf("") }

    val resultTrigger = rememberSaveable { mutableStateOf(false) }
    var missingGlobalPermissions by remember { mutableStateOf("") }
    val missingBotSafUris = remember { mutableStateListOf<Uri>() }

    if (resultTrigger.value) {
        PopupDialog(
            trigger = resultTrigger,
            icon = {
                ResIcon(
                    iconId = if (succeeded) R.drawable.ic_check_green else R.drawable.ic_fail_red,
                    color = if (succeeded) C.success else C.error,
                )
            },
            content = {
                Text(
                    Str(
                        if (succeeded)
                            R.string.imported_successfully
                        else
                            R.string.import_fail
                    ),
                    color = C.textGrey,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!succeeded) {
                    Text(errorStr, color = C.error)
                }

                if (succeeded &&
                    (missingGlobalPermissions.isNotEmpty() || missingBotSafUris.isNotEmpty())
                ) {

                    // 1. System permissions
                    val prevNames = missingGlobalPermissions
                        .split(",")
                        .filter { it.isNotEmpty() }

                    val missingPermissions = Permission.all()
                        // all previous permissions
                        .filter {
                            prevNames.contains(it::class.java.simpleName.substringAfterLast('$'))
                        }
                        // not granted
                        .filter {
                            !it.isGranted
                        }
                        // map to Wrapper
                        .map {
                            PermissionWrapper(it)
                        }.toMutableList()

                    // 2. Workflow file permissions
                    missingBotSafUris
                        .filter { !it.hasFolderAccess(ctx)  }
                        .forEach {
                            val perm = PermissionType.SafDirAccess(uri = it)
                            missingPermissions += PermissionWrapper(
                                perm,
                                prompt = ctx.getString(R.string.grant_access_to_dir).format("${it.toFolderDisplayName()}")
                            )
                        }

                    if (missingPermissions.isNotEmpty()) {
                        Text(
                            text = Str(R.string.missing_permissions),
                            color = C.warning,
                        )

                        missingPermissions.forEach {
                            GreyLabel(it.perm.desc(ctx))
                        }

                        StrokeButton(
                            label = Str(R.string.grant_permissions),
                            color = C.teal200,
                        ) {
                            G.permissionChain.ask(ctx, missingPermissions) {
                                // After finished, refresh `SAF permissions`.
                                // No need to refresh global permissions, they trigger recomposition, but SAF doesn't.
                                missingBotSafUris.apply {
                                    clear()
                                    addAll(missingBotSafUris.filter {
                                        !it.hasFolderAccess(ctx)
                                    })
                                }
                            }
                        }
                    }
                }
            },
            onDismiss = {
                if (succeeded) {
                    Launcher.restartProcess(ctx)
                }
            }
        )
    }

    fun chooseImportFile(includeSpamDB: Boolean) {
        FileChooser.popupRead(
            init = InitFile(
                filename = "",
                mimeType = MIME_GZ,
                rememberDirTag = Backup_Last_Dir_Tag,
            ),
            onResult = { uri ->
                if (uri != null) {
                    val bytes = readDataFromUri(ctx, uri)
                        ?: return@popupRead

                    try {
                        val newCfg = Configs.fromByteArray(bytes)
                        newCfg.apply(ctx, includeSpamDB)

                        missingGlobalPermissions = newCfg.permissions.allEnabledNames
                        missingBotSafUris.apply {
                            clear()
                            addAll(missingBotSafUris(newCfg.bots.bots))
                        }
                        succeeded = true
                        resultTrigger.value = true

                        // Fire an event to notify the configuration has changed,
                        // for example, the history cleanup schedule should restart
                        Events.configImported.fire()
                    } catch (e: Exception) {
                        succeeded = false
                        errorStr = e.message ?: ""
                        resultTrigger.value = true
                    }
                }
            },

        )
    }

    DropdownWrapper(
        items = listOf(
            LabelItem(
                label = Str(R.string.include_spam_db)
            ) {
                chooseImportFile(true)
            }
        )
    ) { expanded ->

        LongPressButton(
            label = Str(R.string.import_),
            color = C.infoBlue,
            onClick = {
                chooseImportFile(false)
            },
            onLongClick = {
                expanded.value = true
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackupRestore() {
    LabeledRow(
        R.string.backup,
        helpTooltip = Str(R.string.help_backup),
        content = {
            FlowRowSpaced(8) {
                ExportButton()
                ImportButton()
            }
        }
    )
}