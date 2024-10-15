package spam.blocker.ui.setting.misc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import spam.blocker.Events
import spam.blocker.R
import spam.blocker.config.Configs
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
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


@Composable
fun BackupRestore() {
    val ctx = LocalContext.current

    LabeledRow(
        R.string.backup,
        helpTooltipId = R.string.help_backup,
        content = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // export
                val fileWriter = rememberFileWriteChooser()
                fileWriter.Compose()

                val labels = listOf(
                    R.string.include_spam_db,
                    R.string.configuration_only,
                )
                val icons = listOf(
                    R.drawable.ic_db_and_config,
                    R.drawable.ic_settings
                )
                val exportMenuItems = labels.mapIndexed { index, labelId ->
                    LabelItem(
                        label = Str(labelId),
                        icon = { GreyIcon(icons[index]) }
                    ) {
                        val includeSpamDB = index == 0

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
                }
                MenuButton(
                    label = Str(R.string.export),
                    items = exportMenuItems,
                    color = Teal200,
                )

                // import
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

                val importMenuItems = labels.mapIndexed { index, labelId ->
                    LabelItem(
                        label = Str(labelId),
                        icon = { GreyIcon(icons[index]) }
                    ) {
                        val includeSpamDB = index == 0

                        fileReader.popup { _, raw ->
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
                }

                MenuButton(
                    label = Str(R.string.import_),
                    items = importMenuItems,
                    color = SkyBlue,
                )
            }
        }
    )
}