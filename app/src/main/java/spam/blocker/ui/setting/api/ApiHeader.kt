package spam.blocker.ui.setting.api

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import spam.blocker.G
import spam.blocker.R
import spam.blocker.db.Api
import spam.blocker.db.ApiTable
import spam.blocker.service.bot.HttpDownload
import spam.blocker.service.bot.ParseIncomingNumber
import spam.blocker.service.bot.ParseQueryResult
import spam.blocker.service.bot.botJson
import spam.blocker.ui.M
import spam.blocker.ui.setting.LabeledRow
import spam.blocker.ui.setting.SettingLabel
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
    preset: ApiPreset,
    api: Api,
    onOk: Lambda,
) {
    val ctx = LocalContext.current

    // All attributes
    val formFields = remember {
        preset.authConfig!!.copy().formLabels.map {
            FormField(ctx.getString(it))
        }
    }

    PopupDialog(
        trigger = trigger,
        buttons = {
            // OK button
            StrokeButton(label = Str(R.string.ok), color = Teal200) {
                preset.authConfig!!.overrider(api.actions, formFields.map { it.value })
                trigger.value = false
                onOk()
            }
        }
    ) {
        // A guide for how to obtain the api key
        HtmlText(Str(preset.authConfig!!.tooltipId))

        // Show all required fields as a form
        formFields.forEach {
            FormInputField(it)
        }
    }
}

@Composable
fun ApiHeader(
    vm: ApiViewModel,
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
                ApiTable.addNewRecord(ctx, newApi)

                // 2. reload UI
                G.apiVM.reload(ctx)
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
                enabled = false,
            )

            // 1. add to db
            ApiTable.addNewRecord(ctx, newApi)
            // 2. reload UI
            G.apiVM.reload(ctx)
        }
    }

    var currentPreset = remember { mutableStateOf<ApiPreset?>(null)}
    val authFormTrigger = remember { mutableStateOf(false) }
    if (authFormTrigger.value) {
        ApiAuthConfigDialog(
            trigger = authFormTrigger,
            preset = currentPreset.value!!,
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
                    actions = listOf(
                        ParseIncomingNumber(),
                        HttpDownload(),
                        ParseQueryResult(),
                    )
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
        ret += ApiPresets.map { preset ->
            val api = preset.newInstance(ctx)
            LabelItem(
                label = api.desc,
                tooltip = ctx.getString(preset.tooltipId)
            ) {
                currentPreset.value = preset
                initialApiToEdit.value = api

                // If the preset doesn't require authorization, such as API_KEY/username/password,
                //  create the actions directly.
                if (preset.authConfig == null) {
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
                    labelId = R.string.api_services,
                )
                if (vm.listCollapsed.value) {
                    GreyIcon16(
                        iconId = R.drawable.ic_dropdown_arrow,
                    )
                }
            }
        },
        helpTooltipId = R.string.help_instant_query,
    ) {
        MenuButton(
            label = Str(R.string.new_),
            color = SkyBlue,
            items = dropdownItems,
        )
    }
}
