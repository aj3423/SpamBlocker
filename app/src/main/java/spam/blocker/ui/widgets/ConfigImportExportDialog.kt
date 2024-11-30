package spam.blocker.ui.widgets

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import spam.blocker.R
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Teal200
import spam.blocker.util.Clipboard
import spam.blocker.util.Lambda1

@Composable
fun ConfigImportDialog(
    trigger: MutableState<Boolean>,
    applyContent: Lambda1<String>,
) {
    if (trigger.value) {

        var text by remember { mutableStateOf("") }

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
                    StrokeButton(
                        label = Str(R.string.import_),
                        color = Teal200
                    ) {
                        succeeded = try {
                            applyContent(text)
                            true
                        } catch (_: Exception) {
                            false
                        }
                        importResultTrigger.value = true
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
@Composable
fun ConfigExportDialog(
    trigger: MutableState<Boolean>,
    initialText: String,
) {
    val ctx = LocalContext.current

    if (trigger.value) {

        var text by remember { mutableStateOf(initialText) }

        PopupDialog(
            trigger = trigger,
            buttons = {
                    StrokeButton(
                        label = Str(R.string.copy),
                        color = Teal200
                    ) {
                        Clipboard.copy(ctx, text)
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
