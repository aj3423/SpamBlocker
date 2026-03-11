@file:OptIn(ExperimentalMaterial3Api::class)

package spam.blocker.ui.setting.misc

import android.annotation.SuppressLint
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.core.text.HtmlCompat
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.widgets.BalloonWrapper
import spam.blocker.ui.widgets.FlowRowSpaced
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Clipboard
import spam.blocker.util.Lambda1
import spam.blocker.util.logi


data class FaqBox(
    val label: String,
    val color: Color,
    val helpTooltip: String,
    val onCustomLinkClick: Lambda1<String>? = null
)

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun FAQ() {
    val C = G.palette
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
                            color = C.priority,
                            helpTooltip = ctx.getString(R.string.faq_priority)
                        ),
                        FaqBox(
                            label = ctx.getString(R.string.regex_pattern),
                            color = C.teal200,
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
                            color = C.infoBlue,
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


    StrokeButton(
        label = Str(R.string.faq),
        color = C.infoBlue,
        onClick = {
            popupTrigger.value = true
        }
    )
}
