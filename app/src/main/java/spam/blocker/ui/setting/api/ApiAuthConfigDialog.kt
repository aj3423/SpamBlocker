package spam.blocker.ui.setting.api

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.widgets.FormField
import spam.blocker.ui.widgets.FormInputField
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda2

// This dialog makes it easier for user to input API_KEY rather than manually editing
//  the tags in http url/header.
@Composable
fun ApiAuthConfigDialog(
    trigger: MutableState<Boolean>,
    authConfig: AuthConfig,

    hasReportApi: Boolean = true, // show a switchbox "Enable Report" if the API also supports reporting numbers

    onOk: Lambda2<Boolean, List<FormField>>, // <also report, form fields>
) {
    val ctx = LocalContext.current
    val C = G.palette

    // All attributes
    val formFields = remember {
        authConfig.formLabels.map {
            FormField(label = ctx.getString(it))
        }
    }
    var alsoReport by remember { mutableStateOf(false) }

    var errStr by remember { mutableStateOf<String?>(null) }
    val validatePopupTrigger = remember { mutableStateOf(false) }

    PopupDialog(
        trigger = validatePopupTrigger,
    ) {
        Text(
            text = errStr ?: Str(R.string.checking_auth_credential),
            color = if (errStr == null) C.textGrey else C.error,
        )
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            // OK button
            StrokeButton(label = Str(R.string.ok), color = C.teal200) {
                errStr = null // clear previous error
                validatePopupTrigger.value = true

                CoroutineScope(IO).launch {
                    authConfig.validator(
                        ctx, formFields.map { it.value },
                    ) { err ->
                        errStr = err
                        if (err == null) { // valid
                            validatePopupTrigger.value = false

                            trigger.value = false
                            onOk(alsoReport, formFields)
                        }
                    }
                }
            }
        }
    ) {
        // A guide for how to obtain the api key
        HtmlText(Str(authConfig.tooltipId), modifier = M.fillMaxWidth())

        // Show all required fields as a form
        formFields.forEach {
            FormInputField(it)
        }

        // The switchbox "Enable Reporting"
        if (hasReportApi) {
            LabeledRow(
                R.string.enable_reporting_number,
                helpTooltip = Str(R.string.help_enable_reporting_number),
                content = {
                    SwitchBox(alsoReport) {
                        alsoReport = it
                    }
                }
            )
        }
    }
}