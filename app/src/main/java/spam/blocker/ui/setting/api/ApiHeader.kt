package spam.blocker.ui.setting.api

import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.def.Def
import spam.blocker.service.bot.botJson
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingLabel
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
import spam.blocker.ui.widgets.RowVCenterSpaced
import spam.blocker.ui.widgets.Str
import spam.blocker.ui.widgets.StrokeButton
import spam.blocker.util.Lambda
import kotlin.Boolean

// This dialog makes it easier for user to input API_KEY rather than manually editing
//  the tags in http url/header.
@Composable
fun ApiAuthConfigDialog(
    trigger: MutableState<Boolean>,
    authConfig: AuthConfig,
    api: Api,
    onOk: Lambda,
) {
    val ctx = LocalContext.current

    // All attributes
    val formFields = remember {
        authConfig.formLabels.map {
            FormField(ctx.getString(it))
        }
    }

    val errorTrigger = remember { mutableStateOf(false) }
    PopupDialog(
        trigger = errorTrigger,
    ) {
        Text(
            text = Str(R.string.missing_credentials),
            color = Salmon,
//            fontSize = fontSize,
//            fontWeight = fontWeight,
//            maxLines = maxLines,
//            overflow = overflow,
        )
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            // OK button
            StrokeButton(label = Str(R.string.ok), color = Teal200) {
                val isValid = authConfig.validator(
                    formFields.map { it.value }
                )
                if (isValid) {
                    // Replace the api_key in the http request.
                    authConfig.preProcessor(api.actions, formFields.map { it.value })
                    trigger.value = false
                    onOk()
                } else {
                    errorTrigger.value = true
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
    }
}

@Composable
fun ApiHeader(
    vm: ApiViewModel,
    presets: List<ApiPreset>,
) {
    val ctx = LocalContext.current

    var initialApiToEdit = remember { mutableStateOf(Api()) }
    val addTrigger = rememberSaveable { mutableStateOf(false) }
    if (addTrigger.value) {
        EditApiDialog(
            trigger = addTrigger,
            initial = initialApiToEdit.value,
            onSave = { newApi ->
                // 1. add to db
                vm.table.addNewRecord(ctx, newApi)

                // 2. reload UI
                vm.reloadDb(ctx)
            }
        )
    }

    val importTrigger = remember { mutableStateOf(false) }
    if (importTrigger.value) {
        ConfigImportDialog(
            trigger = importTrigger,
        ) { configJson ->
            val newApi = botJson.decodeFromString<Api>(configJson).copy(
                id = 0,
            )

            // 1. add to db
            vm.table.addNewRecord(ctx, newApi)
            // 2. reload UI
            vm.reloadDb(ctx)
        }
    }

    var currentPreset = remember { mutableStateOf<ApiPreset?>(null) }
    val authFormTrigger = remember { mutableStateOf(false) }
    if (authFormTrigger.value) {
        ApiAuthConfigDialog(
            trigger = authFormTrigger,
            authConfig = currentPreset.value!!.newAuthConfig()!!,
            api = initialApiToEdit.value,
        ) {
            addTrigger.value = true
        }
    }

    val dropdownItems = remember {
        val ret = mutableListOf(
            LabelItem(
                label = ctx.getString(R.string.customize),
                icon = { GreyIcon(R.drawable.ic_note) }
            ) {
                initialApiToEdit.value = Api(
                    actions = if (vm.forType == Def.ForApiQuery)
                        defApiQueryActions
                    else
                        defApiReportActions
                )
                addTrigger.value = true
            },
            LabelItem(
                label = ctx.getString(R.string.import_),
                icon = { GreyIcon(R.drawable.ic_backup_import) }
            ) {
                importTrigger.value = true
            },
            DividerItem(),
        )
        ret += presets.map { preset ->
            val desc = preset.newApi(ctx).desc
            LabelItem(
                label = desc,
                tooltip = ctx.getString(preset.tooltipId)
            ) {
                currentPreset.value = preset
                initialApiToEdit.value = preset.newApi(ctx)

                // If the preset doesn't require authorization, such as API_KEY/username/password,
                //  show a dialog for configuring it.
                // Otherwise, create the actions directly.
                val authConfig = preset.newAuthConfig()
                if (authConfig == null) {
                    addTrigger.value = true
                } else {
                    // If it requires authorization, show a config dialog
                    authFormTrigger.value = true
                }
            }
        }
        ret
    }

    LabeledRow(
        modifier = M.clickable { vm.toggleCollapse(ctx) },
        label = {
            RowVCenterSpaced(4) {
                SettingLabel(
                    labelId = if(vm.forType == Def.ForApiQuery) R.string.query_api else R.string.report_api,
                )
                if (vm.listCollapsed.value) {
                    GreyIcon16(
                        iconId = R.drawable.ic_dropdown_arrow,
                    )
                }
            }
        },
        helpTooltipId = if(vm.forType == Def.ForApiQuery)
            R.string.help_instant_query
        else
            R.string.help_auto_report,
    ) {
        MenuButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            items = dropdownItems,
        )
    }
}
