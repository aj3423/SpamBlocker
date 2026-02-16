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
import spam.blocker.db.IApi
import spam.blocker.service.bot.IAction
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.FormField
import spam.blocker.ui.widgets.FormInputField
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.Lambda

// This dialog makes it easier for user to input API_KEY rather than manually editing
//  the tags in http url/header.
@Composable
fun ApiAuthConfigDialog(
    trigger: MutableState<Boolean>,
    authConfig: AuthConfig,
    actions: List<IAction>,

    reportApi: IApi? = null, // show a switchbox "Enable Report" if the API also supports reporting numbers
    reportLabel: Int = R.string.enable_reporting_number,
    reportTooltip: Int = R.string.help_enable_reporting_number,

    onOk: Lambda,
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    // All attributes
    val formFields = remember {
        authConfig.formLabels.map {
            FormField(label = ctx.getString(it))
        }
    }
    var isReportEnabled by remember { mutableStateOf(false) }

    var errStr by remember { mutableStateOf<String?>(null) }
    val validatePopupTrigger = remember { mutableStateOf(false) }

    PopupDialog(
        trigger = validatePopupTrigger,
    ) {
        Text(
            text = errStr ?: Str(R.string.checking_auth_credential),
            color = if (errStr == null) C.textGrey else Salmon,
        )
    }

    fun addReportApi(formFields: List<FormField>?) {
        if (isReportEnabled) {
            authConfig.preProcessor(reportApi!!.actions, formFields!!.map { it.value })

            val vm = G.apiReportVM
            // 1. add to db
            vm.table.addNewRecord(ctx, reportApi)
            // 2. reload UI
            vm.reloadDb(ctx)
            // 3. expand the header to reveal it
            G.apiReportVM.listCollapsed.value = false
        }
    }
    PopupDialog(
        trigger = trigger,
        buttons = {
            // OK button
            StrokeButton(label = Str(R.string.ok), color = Teal200) {
                errStr = null // clear previous error
                validatePopupTrigger.value = true

                CoroutineScope(IO).launch {
                    authConfig.validator(
                        ctx, formFields.map { it.value },
                    ) { err ->
                        errStr = err
                        if (err == null) { // valid
                            validatePopupTrigger.value = false
                            // Replace the api_key in the http request.
                            authConfig.preProcessor(actions, formFields.map { it.value })
                            trigger.value = false
                            onOk()
                            if (isReportEnabled) {
                                addReportApi(formFields)
                            }
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

        if (reportApi != null) {
            LabeledRow(
                reportLabel,
                helpTooltip = Str(reportTooltip),
                content = {
                    SwitchBox(isReportEnabled) {
                        isReportEnabled = it
                    }
                }
            )
        }
    }
}