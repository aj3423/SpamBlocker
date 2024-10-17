package spam.blocker.ui.setting.bot

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Bot
import spam.blocker.db.BotTable
import spam.blocker.service.bot.botJson
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.GreyLabel
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrInputBox
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Clipboard
import java.util.UUID

@Composable
fun BotImportExportDialog(
    trigger: MutableState<Boolean>,
    initialText: String,
    isExport: Boolean,
) {
    val ctx = LocalContext.current

    if (trigger.value) {

        var text by remember { mutableStateOf(initialText) }

        var succeeded by remember { mutableStateOf(false) }

        val importResultTrigger = remember { mutableStateOf(false) }
        PopupDialog(
            trigger = importResultTrigger,
            onDismiss = { trigger.value = false }, // close import dialog
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
            }
        )

        PopupDialog(
            trigger = trigger,
            buttons = {
                if (isExport) { // Export
                    StrokeButton(
                        label = Str(R.string.copy),
                        color = Teal200
                    ) {
                        Clipboard.copy(ctx, text)
                    }
                } else { // Import
                    StrokeButton(
                        label = Str(R.string.import_),
                        color = Teal200
                    ) {
                        try {
                            val newBot = botJson.decodeFromString<Bot>(text).copy(
                                id = 0,
                                enabled = false,
                                workUUID = UUID.randomUUID().toString(),
                            )
                            
                            // 1. add to db
                            BotTable.addNewRecord(ctx, newBot)
                            // 2. reload UI
                            G.botVM.reload(ctx)

                            succeeded = true
                        } catch (e: Exception) {
                            succeeded = false
                        }
                        importResultTrigger.value = true
                    }
                }
            }
        ) {
            StrInputBox(
                label = { GreyLabel(Str(R.string.config_text)) },
                text = text,
                maxLines = 20,
                onValueChange = {
                    text = it
                }
            )
        }
    }
}
