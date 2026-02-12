package spam.blocker.ui.setting.misc

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.Events
import spam.blocker.G
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.db.SpamTable
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.DarkOrange
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LongPressButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.rememberFileReadChooser
import spam.blocker.ui.widgets.rememberFileWriteChooser
import spam.blocker.util.A
import spam.blocker.util.Launcher
import spam.blocker.util.Permission
import spam.blocker.util.PermissionWrapper
import spam.blocker.util.formatAnnotated
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportButton() {
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
            "$total".A(Salmon), "$estimate".A(Teal200)
        ))
    }

    // Show a system file chooser
    val fileWriter = rememberFileWriteChooser()
    fileWriter.Compose()

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

        if (includeSpamDB) progressTrigger.value = false

        fileWriter.popup(
            filename = fn,
            content = compressed,
        )
    }


    DropdownWrapper(
        items = listOf(
            LabelItem(
                label = Str(R.string.include_spam_db)
            ) {
                progressTrigger.value = true
                coroutine.launch(IO) {
                    chooseExportFile(includeSpamDB = true)
                }
            }
        )
    ) { expanded ->
        LongPressButton(
            label = Str(R.string.export),
            color = Teal200,
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

    val fileReader = rememberFileReadChooser()
    fileReader.Compose()

    var succeeded by remember { mutableStateOf(false) }
    var errorStr by remember { mutableStateOf("") }

    val resultTrigger = rememberSaveable { mutableStateOf(false) }
    var prevPermissions by remember { mutableStateOf("") }

    if (resultTrigger.value) {
        PopupDialog(
            trigger = resultTrigger,
            icon = {
                ResIcon(
                    iconId = if (succeeded) R.drawable.ic_check_green else R.drawable.ic_fail_red,
                    color = if (succeeded) LocalPalette.current.pass else LocalPalette.current.block,
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
                    color = LocalPalette.current.textGrey,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!succeeded) {
                    Text(errorStr, color = Salmon)
                }

                if (succeeded && prevPermissions.isNotEmpty()) {

                    val prevNames = prevPermissions
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
                        }
                    if (missingPermissions.isNotEmpty()) {
                        Text(
                            text = Str(R.string.missing_permissions),
                            color = DarkOrange,
                        )

                        missingPermissions.forEach {
                            GreyLabel(it.perm.desc(ctx))
                        }

                        StrokeButton(
                            label = Str(R.string.grant_permissions),
                            color = Teal200,
                        ) {
                            G.permissionChain.ask(ctx, missingPermissions) { }
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
        fileReader.popup(
            mimeTypes = arrayOf("application/octet-stream", "application/gzip")
        ) { _, bytes ->
            if (bytes == null)
                return@popup

            try {
                val newCfg = Configs.fromByteArray(bytes)
                newCfg.apply(ctx, includeSpamDB)

                prevPermissions = newCfg.permissions.allEnabledNames
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
            color = SkyBlue,
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