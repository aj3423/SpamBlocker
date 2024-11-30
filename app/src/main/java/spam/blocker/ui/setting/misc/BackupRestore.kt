package spam.blocker.ui.setting.misc

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.DropdownWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.LongPressButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.rememberFileReadChooser
import spam.blocker.ui.widgets.rememberFileWriteChooser
import spam.blocker.util.Algorithm.b64Decode
import spam.blocker.util.Algorithm.compressString
import spam.blocker.util.Algorithm.decompressToString
import spam.blocker.util.Launcher
import java.time.LocalDate
import java.time.format.DateTimeFormatter


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportButton() {
    val ctx = LocalContext.current

    val fileWriter = rememberFileWriteChooser()
    fileWriter.Compose()

    fun chooseExportFile(includeSpamDB: Boolean) {
        // prepare file name
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd")
        val ymd = LocalDate.now().format(formatter)
        val fn = "SpamBlocker.${ymd}${if (includeSpamDB) ".db" else ""}.gz"

        // prepare file content
        val curr = Configs()
        curr.load(ctx, includeSpamDB)
        val content = compressString(curr.toJsonString())

        fileWriter.popup(
            filename = fn,
            content = content,
        )
    }

    DropdownWrapper(
        items = listOf(
            LabelItem(
                label = Str(R.string.include_spam_db)
            ) {
                chooseExportFile(true)
            }
        )
    ) { expanded ->
        LongPressButton(
            label = Str(R.string.export),
            color = Teal200,
            onClick = {
                chooseExportFile(false)
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
    val resultTrigger = rememberSaveable { mutableStateOf(false) }

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
            },
            onDismiss = {
                if (succeeded)
                    Launcher.selfRestart(ctx)
            }
        )
    }

    fun chooseImportFile(includeSpamDB: Boolean) {
        fileReader.popup(
            mimeTypes = arrayOf("application/octet-stream", "application/gzip")
        ) { _, raw ->
            if (raw == null)
                return@popup

            fun onDecodeSuccess(str: String) {
                val newCfg = Configs.createFromJson(str)
                newCfg.apply(ctx, includeSpamDB)

                succeeded = true
                resultTrigger.value = true

                // Fire an event to notify the configuration has changed,
                // for example, the history cleanup schedule should restart
                Events.configImported.fire()
            }

            fun onDecodeFail() {
                succeeded = false
                resultTrigger.value = true
            }

            try {
                // for history compatibility, text file contains b64(gzip)
                val jsonStr = decompressToString(b64Decode(String(raw)))
                onDecodeSuccess(jsonStr)
            } catch (_: Exception) {
                try {
                    // try gzip compressed
                    val jsonStr = decompressToString(raw)
                    onDecodeSuccess(jsonStr)
                } catch (e: Exception) {
                    // try plain json string
                    try {
                        val jsonStr = String(raw)
                        onDecodeSuccess(jsonStr)
                    } catch (e: Exception) {
                        onDecodeFail()
                    }
                }
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
        helpTooltipId = R.string.help_backup,
        content = {
            FlowRowSpaced(8) {
                ExportButton()
                ImportButton()
            }
        }
    )
}