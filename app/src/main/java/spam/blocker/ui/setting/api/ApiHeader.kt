package spam.blocker.ui.setting.api

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.IApi
import spam.blocker.db.QueryApi
import spam.blocker.db.ReportApi
import spam.blocker.def.Def
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.theme.LocalPalette
import spam.blocker.ui.theme.Salmon
import spam.blocker.ui.theme.SkyBlue
import spam.blocker.ui.theme.Teal200
import spam.blocker.ui.widgets.ConfigImportDialog
import spam.blocker.ui.widgets.DividerItem
import spam.blocker.ui.widgets.FormField
import spam.blocker.ui.widgets.FormInputField
import spam.blocker.ui.widgets.GreyIcon
import spam.blocker.ui.widgets.GreyIcon16
import spam.blocker.ui.widgets.HtmlText
import spam.blocker.ui.widgets.LabelItem
import spam.blocker.ui.widgets.MenuButton
import spam.blocker.ui.widgets.PopupDialog
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.ui.widgets.SwitchBox
import spam.blocker.util.BotJson
import spam.blocker.util.Lambda2

// This dialog makes it easier for user to input API_KEY rather than manually editing
//  the tags in http url/header.
@Composable
fun ApiAuthConfigDialog(
    trigger: MutableState<Boolean>,
    authConfig: AuthConfig,
    api: IApi,
    showReportOption: Boolean,
    onOk: Lambda2<Boolean, List<FormField>?>, // <EnableReport, FormFields>
) {
    val ctx = LocalContext.current
    val C = LocalPalette.current

    // All attributes
    val formFields = remember {
        authConfig.formLabels.map {
            FormField(label = ctx.getString(it))
        }
    }
    var enableReport by remember { mutableStateOf(false) }

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

    PopupDialog(
        trigger = trigger,
        buttons = {
            // OK button
            StrokeButton(label = Str(R.string.ok), color = Teal200) {
                validatePopupTrigger.value = true

                CoroutineScope(IO).launch {
                    authConfig.validator(
                        ctx, formFields.map { it.value },
                    ) { err ->
                        errStr = err
                        if (err == null) { // valid
                            validatePopupTrigger.value = false
                            // Replace the api_key in the http request.
                            authConfig.preProcessor(api.actions, formFields.map { it.value })
                            trigger.value = false
                            onOk(enableReport, formFields)
                        }
                    }
                }
            }
        }
    ) {
        // A guide for how to obtain the api key
        HtmlText(Str(authConfig.tooltipId))

        // Show all required fields as a form
        formFields.forEach {
            FormInputField(it)
        }

        if (showReportOption) {
            LabeledRow(
                R.string.enable_reporting_number,
                helpTooltip = Str(R.string.help_enable_reporting_number),
                content = {
                    SwitchBox(enableReport) {
                        enableReport = it
                    }
                }
            )
        }
    }
}

@Composable
fun ApiHeader(
    vm: ApiViewModel,
    presets: List<ApiPreset>,
) {
    val ctx = LocalContext.current

    var tappedPreset = remember { mutableStateOf<ApiPreset?>(null) }
    var initialApi = remember { mutableStateOf<IApi?>(null) }

    fun addApiToDB(ctx: Context, newApi: IApi) {
        // 1. add to db
        vm.table.addNewRecord(ctx, newApi)

        // 2. reload UI
        vm.reloadDb(ctx)
    }
    // When creating PhoneBlock QueryApi, also create its ReportApi
    fun addReportingApiToDB(ctx: Context, newApi: IApi) {
        val vm = G.apiReportVM
        // 1. add to db
        vm.table.addNewRecord(ctx, newApi)

        // 2. reload UI
        vm.reloadDb(ctx)
    }
    val addTrigger = rememberSaveable { mutableStateOf(false) }
    if (addTrigger.value) {
        EditApiDialog(
            trigger = addTrigger,
            initial = initialApi.value!!,
            onSave = { newApi ->
                addApiToDB(ctx, newApi)
            }
        )
    }

    val importTrigger = remember { mutableStateOf(false) }
    if (importTrigger.value) {
        ConfigImportDialog(
            trigger = importTrigger,
        ) { configJson ->
            val newApi = if (vm.forType == Def.ForApiQuery) {
                BotJson.decodeFromString<QueryApi>(configJson).copy(id = 0)
            } else {
                BotJson.decodeFromString<ReportApi>(configJson).copy(id = 0)
            }

            // 1. add to db
            vm.table.addNewRecord(ctx, newApi)
            // 2. reload UI
            vm.reloadDb(ctx)
        }
    }

    val authFormTrigger = remember { mutableStateOf(false) }
    if (authFormTrigger.value) {
        ApiAuthConfigDialog(
            trigger = authFormTrigger,
            authConfig = tappedPreset.value!!.newAuthConfig()!!,
            api = initialApi.value!!,
            showReportOption = tappedPreset.value!!.newReportApi != null
        ) { enableReport, formFields ->
            addApiToDB(ctx, initialApi.value!!)
            if (enableReport) {
                var reportingApi = tappedPreset.value!!.newReportApi!!(ctx)
                val authConfig = tappedPreset.value!!.newAuthConfig()!!
                authConfig.preProcessor(reportingApi.actions, formFields!!.map { it.value })
                addReportingApiToDB(ctx, reportingApi)
            }
        }
    }

    val dropdownItems = remember {
        val ret = mutableListOf(
            LabelItem(
                label = ctx.getString(R.string.customize),
                leadingIcon = { GreyIcon(R.drawable.ic_note) }
            ) {
                initialApi.value = if(vm.forType == Def.ForApiQuery) {
                    QueryApi(actions = defApiQueryActions)
                } else {
                    ReportApi(actions = defApiReportActions)
                }

                addTrigger.value = true
            },
            LabelItem(
                label = ctx.getString(R.string.import_),
                leadingIcon = { GreyIcon(R.drawable.ic_backup_import) }
            ) {
                importTrigger.value = true
            },
            DividerItem(),
        )

        // Api Presets: PhoneBlock, Groq, ...
        ret += presets.map { preset ->
            val desc = preset.newApi(ctx).desc
            LabelItem(
                label = desc,
                tooltip = ctx.getString(preset.tooltipId),
                leadingIcon = preset.leadingIconId?.let{ iconId-> { GreyIcon16(iconId) } }
            ) {
                tappedPreset.value = preset
                initialApi.value = preset.newApi(ctx)

                // If the preset requires authorization, such as API_KEY/username/password,
                //  show a dialog asking for it.
                // Otherwise, create the actions directly.
                val authConfig = preset.newAuthConfig()
                if (authConfig == null) {
                    addApiToDB(ctx, preset.newApi(ctx))
                } else {
                    // If it requires authorization, show a config dialog
                    authFormTrigger.value = true
                }
            }
        }
        ret
    }

    LabeledRow(
        labelId = if (vm.forType == Def.ForApiQuery) R.string.query_api else R.string.report_api,
        modifier = M.clickable { vm.toggleCollapse(ctx) },
        isCollapsed = vm.listCollapsed.value,
        toggleCollapse = { vm.toggleCollapse(ctx) },
        helpTooltip = if (vm.forType == Def.ForApiQuery)
            Str(R.string.help_instant_query)
        else
            Str(R.string.help_report_number),
    ) {
        MenuButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            items = dropdownItems,
        )
    }
}
