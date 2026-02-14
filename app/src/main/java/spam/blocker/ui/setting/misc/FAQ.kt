@file:OptIn(ExperimentalMaterial3Api::class)

package spam.blocker.ui.setting.misc

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.launch
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.theme.Priority
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.BalloonWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.ResIcon
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.rememberFileWriteChooser
import spam.blocker.util.Clipboard
import spam.blocker.util.Lambda1
import spam.blocker.util.logi


data class FaqBox(
    val label: String,
    val color: Color,
    val helpTooltip: String,
    val icon: Int,
    val onCustomLinkClick: Lambda1<String>? = null
)

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun FAQ() {
    val ctx = LocalContext.current

    val popupTrigger = rememberSaveable { mutableStateOf(false) }

    if (popupTrigger.value) {
        PopupDialog(
            trigger = popupTrigger,
            content = {
                val items = remember {
                    listOf(
                        FaqBox(
                            label = ctx.getString(R.string.priority),
                            color = Priority,
                            icon = R.drawable.ic_priority,
                            helpTooltip = ctx.getString(R.string.faq_priority)
                        ),
                        FaqBox(
                            label = ctx.getString(R.string.regex_pattern),
                            color = Teal200,
                            icon = R.drawable.ic_regex,
                            helpTooltip = ctx.getString(R.string.faq_regex).format(
                                ctx.getString(R.string.ai_regex_prompt)
                            ),
                            onCustomLinkClick = { tagClicked ->
                                logi("clicked : $tagClicked")
                                when (tagClicked) {
                                    // Copy prompt to clipboard
                                    "copy" -> {
                                        val withTags = ctx.getString(R.string.ai_regex_prompt)
                                        // clear all <..></..> to get plain text
                                        val plainPrompt = HtmlCompat.fromHtml(withTags, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()

                                        Clipboard.copy(ctx, plainPrompt)
                                        Toast.makeText(ctx, ctx.getString(R.string.copied_to_clipboard), Toast.LENGTH_SHORT).show();
                                    }
                                    // Launch browser: https://chat.cerebras.ai/
                                    "try_it" -> {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW,
                                            "https://chat.cerebras.ai".toUri()))
                                    }
                                }
                            }
                        ),
                        FaqBox(
                            label = ctx.getString(R.string.import_numbers),
                            color = SkyBlue,
                            icon = R.drawable.ic_import,
                            helpTooltip = ctx.getString(R.string.faq_import_numbers).format(
                                "$REPO/issues?q=label:import_plain_text",
                                "$REPO/issues?q=label:import_csv",
                                "$REPO/issues?q=label:import_xml",
                                "$REPO/issues?q=label:import_json",
                            )
                        ),
                    )
                }


                val scope = rememberCoroutineScope()

                FlowRowSpaced(
                    space = 20,
                    vSpace = 30,
                ) {
                    items.forEach { faqBox ->
                        BalloonWrapper(
                            tooltip = faqBox.helpTooltip,
                            onCustomLinkClick = faqBox.onCustomLinkClick
                        ) { tooltipState ->
                            StrokeButton(
                                label = faqBox.label,
                                color = faqBox.color,
                                icon = { ResIcon(faqBox.icon, color = faqBox.color, modifier = M.size(16.dp)) }
                            ) {
                                scope.launch {
                                    tooltipState.show()
                                }
                            }
                        }
                    }
                }
            }
        )
    }

    val fileWriter = rememberFileWriteChooser()
    fileWriter.Compose()

    StrokeButton(
        label = Str(R.string.faq),
        color = SkyBlue,
        onClick = {
            popupTrigger.value = true
        },
        onLongClick = {
            fileWriter.popup(
                filename = "SpamBlocker.log",
                content = Logcat.collect().toByteArray(),
            )
        }
    )
}
